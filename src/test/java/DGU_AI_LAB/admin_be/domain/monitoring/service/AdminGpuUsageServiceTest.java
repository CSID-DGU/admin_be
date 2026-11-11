package DGU_AI_LAB.admin_be.domain.monitoring.service;

import DGU_AI_LAB.admin_be.domain.monitoring.client.PrometheusClient.Sample;
import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.AdminGpuUsageResponseDTO;
import DGU_AI_LAB.admin_be.domain.monitoring.service.MyGpuUsageService.Owner;
import DGU_AI_LAB.admin_be.domain.monitoring.service.MyGpuUsageService.Snapshot;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminGpuUsageServiceTest {

    @Mock MyGpuUsageService myGpuUsageService;
    @Mock NodeRepository nodeRepository;
    @InjectMocks AdminGpuUsageService service;

    private static final String C_KNOWN = "a".repeat(64);
    private static final String C_UNKNOWN = "e".repeat(64);

    @org.junit.jupiter.api.BeforeEach
    void nodeExistsByDefault() {
        when(nodeRepository.existsById(anyString())).thenReturn(true);
    }

    private static Sample s(double v, String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return new Sample(m, v);
    }

    private static Snapshot snap(boolean available, List<Sample> procMem, Map<String, Owner> podOwners) {
        List<Sample> util = List.of(s(40, "UUID", "GPU-1", "gpu", "0", "modelName", "RTX 3090"));
        return new Snapshot(util,
                Map.of("GPU-1", s(10240, "UUID", "GPU-1")),
                Map.of("GPU-1", s(14336, "UUID", "GPU-1")),
                procMem, Map.of("GPU-1/" + C_KNOWN + "/11", 3600.0), podOwners, Map.of(), available);
    }

    @Test
    @DisplayName("프로세스 지표가 없으면 GPU 사용률·메모리만 주고 processesAvailable=false, 프로세스는 비운다")
    void processesUnavailableKeepsGpuMetricsOnly() {
        when(myGpuUsageService.snapshot("farm6")).thenReturn(snap(false, List.of(), Map.of()));

        AdminGpuUsageResponseDTO dto = service.getGpuUsage("farm6").get(0);

        assertThat(dto.nodeName()).isEqualTo("farm6");
        assertThat(dto.processesAvailable()).isFalse();
        assertThat(dto.gpus()).hasSize(1);
        AdminGpuUsageResponseDTO.Gpu gpu = dto.gpus().get(0);
        assertThat(gpu.uuid()).isEqualTo("GPU-1");
        assertThat(gpu.model()).isEqualTo("RTX 3090");
        assertThat(gpu.utilizationPercent()).isEqualTo(40);
        assertThat(gpu.memoryUsedMiB()).isEqualTo(10240);
        assertThat(gpu.memoryTotalMiB()).isEqualTo(24576);
        assertThat(gpu.processes()).isEmpty();
    }

    @Test
    @DisplayName("소유자를 못 찾은 프로세스는 소유자 필드가 전부 null(미확인)이고 나머지 정보는 채운다")
    void unresolvedOwnerIsNull() {
        Sample proc = s(2 * 1048576.0, "gpu_uuid", "GPU-1", "container_id", C_UNKNOWN, "pid", "55",
                "process_name", "/home/zed/train.py");
        when(myGpuUsageService.snapshot("farm6")).thenReturn(snap(true, List.of(proc), Map.of()));

        AdminGpuUsageResponseDTO.Process p = service.getGpuUsage("farm6").get(0).gpus().get(0).processes().get(0);

        assertThat(p.ownerName()).isNull();
        assertThat(p.ubuntuUsername()).isNull();
        assertThat(p.userId()).isNull();
        assertThat(p.requestId()).isNull();
        assertThat(p.containerId()).isEqualTo("eeeeeeeeeeee");
        assertThat(p.processName()).isEqualTo("train.py");
        assertThat(p.memoryUsedMiB()).isEqualTo(2);
        assertThat(p.elapsedSeconds()).isZero();
    }

    @Test
    @DisplayName("소유자를 찾으면 이름·계정·사용자·신청 번호를 채우고, 프로세스는 GPU 메모리 큰 순이다")
    void resolvedOwnerAndSortedByMemory() {
        Owner owner = new Owner("u7", 7L, "홍길동", "u7", 70L);
        Sample small = s(1 * 1048576.0, "gpu_uuid", "GPU-1", "container_id", C_UNKNOWN, "pid", "1", "process_name", "/a");
        Sample big = s(5 * 1048576.0, "gpu_uuid", "GPU-1", "container_id", C_KNOWN, "pid", "11", "process_name", "/b/python");
        when(myGpuUsageService.snapshot("farm6")).thenReturn(snap(true, List.of(small, big), Map.of(C_KNOWN, owner)));

        List<AdminGpuUsageResponseDTO.Process> procs = service.getGpuUsage("farm6").get(0).gpus().get(0).processes();

        assertThat(procs).hasSize(2);
        AdminGpuUsageResponseDTO.Process first = procs.get(0);
        assertThat(first.ownerName()).isEqualTo("홍길동");
        assertThat(first.ubuntuUsername()).isEqualTo("u7");
        assertThat(first.userId()).isEqualTo(7L);
        assertThat(first.requestId()).isEqualTo(70L);
        assertThat(first.processName()).isEqualTo("python");
        assertThat(first.elapsedSeconds()).isEqualTo(3600);
        assertThat(procs.get(1).ownerName()).isNull();
    }

    @Test
    @DisplayName("노드 목록이 비어 있으면 빈 목록을 준다")
    void emptyNodeListReturnsEmpty() {
        when(nodeRepository.findAll()).thenReturn(List.of());

        assertThat(service.getGpuUsage(null)).isEmpty();
        assertThat(service.getGpuUsage("  ")).isEmpty();
    }

    @Test
    @DisplayName("전체 조회는 DB의 모든 노드를 이름순으로 주고, 한 노드가 실패해도 그 노드만 빈 GPU로 표시한다")
    void allNodesOneFailureIsolated() {
        when(nodeRepository.findAll()).thenReturn(List.of(new Node("farm2", null, 1, 1), new Node("farm1", null, 1, 1)));
        when(myGpuUsageService.snapshot("farm1")).thenThrow(new RuntimeException("prom down"));
        when(myGpuUsageService.snapshot("farm2")).thenReturn(snap(true, List.of(), Map.of()));

        List<AdminGpuUsageResponseDTO> all = service.getGpuUsage(null);

        assertThat(all).extracting(AdminGpuUsageResponseDTO::nodeName).containsExactly("farm1", "farm2");
        assertThat(all.get(0).processesAvailable()).isFalse();
        assertThat(all.get(0).gpus()).isEmpty();
        assertThat(all.get(1).gpus()).hasSize(1);
    }

    @Test
    @DisplayName("DB에 없는 노드를 지정하면 404")
    void unknownNodeIsNotFound() {
        when(nodeRepository.existsById("ghost")).thenReturn(false);

        assertThatThrownBy(() -> service.getGpuUsage("ghost"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("노드를 지정했는데 GPU 지표 조회가 실패하면 502(EXTERNAL_API_ERROR)")
    void singleNodeMetricFailureIsBadGateway() {
        when(nodeRepository.existsById(anyString())).thenReturn(true);
        when(myGpuUsageService.snapshot("farm6")).thenThrow(new RuntimeException("prom down"));

        assertThatThrownBy(() -> service.getGpuUsage("farm6"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXTERNAL_API_ERROR);
    }
}
