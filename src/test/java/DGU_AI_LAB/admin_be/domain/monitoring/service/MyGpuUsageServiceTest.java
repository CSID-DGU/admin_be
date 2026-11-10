package DGU_AI_LAB.admin_be.domain.monitoring.service;

import DGU_AI_LAB.admin_be.domain.monitoring.client.PrometheusClient;
import DGU_AI_LAB.admin_be.domain.monitoring.client.PrometheusClient.Sample;
import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.MyGpuUsageResponseDTO;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MyGpuUsageServiceTest {

    @Mock PrometheusClient prometheus;
    @Mock RequestRepository requestRepository;
    @Mock NodeRepository nodeRepository;
    @InjectMocks MyGpuUsageService service;

    private static final String C_MINE = "a".repeat(64);
    private static final String C_BOB = "b".repeat(64);
    private static final String C_AMY = "c".repeat(64);
    private static final String C_DOCKER = "d".repeat(64);
    private static final String C_UNKNOWN = "e".repeat(64);

    private Request request(Long userId, String pod, String node) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(userId);
        when(user.getUbuntuUsername()).thenReturn("u" + userId);
        Request r = mock(Request.class);
        when(r.getUser()).thenReturn(user);
        when(r.getPodName()).thenReturn(pod);
        when(r.getNodeName()).thenReturn(node);
        when(r.getStatus()).thenReturn(Status.FULFILLED);
        return r;
    }

    private static Sample s(double v, String... kv) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return new Sample(m, v);
    }

    @Test
    @DisplayName("내 프로세스는 전부, 다른 사용자는 사람 수와 메모리 합계만 담고 식별 정보는 응답에 없다")
    void aggregatesOthersWithoutIdentity() throws Exception {
        Request mine = request(1L, "pod-mine", "node-a");
        when(requestRepository.findByIdWithUser(10L)).thenReturn(Optional.of(mine));
        when(prometheus.querySeries(contains("DCGM_FI_DEV_GPU_UTIL"))).thenReturn(List.of(
                s(40, "UUID", "GPU-1", "gpu", "0", "modelName", "RTX 3090"),
                s(5, "UUID", "GPU-2", "gpu", "1", "modelName", "RTX 3090")));
        when(prometheus.querySeries(contains("DCGM_FI_DEV_FB_USED"))).thenReturn(List.of(
                s(10240, "UUID", "GPU-1"), s(0, "UUID", "GPU-2")));
        when(prometheus.querySeries(contains("DCGM_FI_DEV_FB_FREE"))).thenReturn(List.of(
                s(14336, "UUID", "GPU-1"), s(24576, "UUID", "GPU-2")));
        when(prometheus.querySeries(contains("gpu_process_memory_used_bytes"))).thenReturn(List.of(
                s(2 * 1048576.0, "gpu_uuid", "GPU-1", "container_id", C_MINE, "pid", "11", "process_name", "/usr/bin/python"),
                s(3 * 1048576.0, "gpu_uuid", "GPU-1", "container_id", C_BOB, "pid", "22", "process_name", "/home/bob/train"),
                s(4 * 1048576.0, "gpu_uuid", "GPU-1", "container_id", C_BOB, "pid", "23", "process_name", "/home/bob/eval"),
                s(1 * 1048576.0, "gpu_uuid", "GPU-1", "container_id", C_AMY, "pid", "33", "process_name", "/home/amy/a"),
                // 옛 Docker 컨테이너(사용자 dave)와 소유자를 못 찾는 컨테이너도 다른 사용자로 센다.
                s(5 * 1048576.0, "gpu_uuid", "GPU-1", "container_id", C_DOCKER, "pid", "44", "process_name", "/home/dave/x"),
                s(6 * 1048576.0, "gpu_uuid", "GPU-1", "container_id", C_UNKNOWN, "pid", "55", "process_name", "/x")));
        when(prometheus.querySeries(contains("gpu_process_elapsed_seconds"))).thenReturn(List.of(
                s(3600, "gpu_uuid", "GPU-1", "container_id", C_MINE, "pid", "11")));
        when(prometheus.querySeries(contains("kube_pod_container_info"))).thenReturn(List.of(
                s(1, "container_id", "containerd://" + C_MINE, "pod", "pod-mine"),
                s(1, "container_id", "containerd://" + C_BOB, "pod", "pod-secret-x"),
                s(1, "container_id", "containerd://" + C_AMY, "pod", "pod-secret-y")));
        when(prometheus.querySeries(contains("docker_gpu_user_container_info"))).thenReturn(List.of(
                s(1, "container_id", C_DOCKER.substring(0, 12), "gpu_uuid", "GPU-1", "ubuntu_username", "dave")));
        Request bob = request(2L, "pod-secret-x", "node-a");
        Request amy = request(3L, "pod-secret-y", "node-a");
        when(requestRepository.findAllByPodNameIn(anyCollection())).thenReturn(List.of(mine, bob, amy));

        MyGpuUsageResponseDTO dto = service.getMyGpuUsage(10L, 1L);

        assertThat(dto.gpus()).hasSize(2);
        MyGpuUsageResponseDTO.Gpu g0 = dto.gpus().get(0);
        assertThat(g0.utilizationPercent()).isEqualTo(40);
        assertThat(g0.memoryTotalMiB()).isEqualTo(24576);
        assertThat(g0.myProcesses()).containsExactly(new MyGpuUsageResponseDTO.Process("python", 3600, 2));
        assertThat(g0.otherUserCount()).isEqualTo(4);
        assertThat(g0.otherMemoryMiB()).isEqualTo(19);
        assertThat(dto.gpus().get(1).otherUserCount()).isZero();

        String json = new ObjectMapper().writeValueAsString(dto);
        assertThat(json).doesNotContain("secret").doesNotContain("bob").doesNotContain("amy")
                .doesNotContain("train").doesNotContain("dave").doesNotContain("\"pid\"");
    }

    @Test
    @DisplayName("남의 신청이면 거부한다")
    void rejectsOthersRequest() {
        Request other = mock(Request.class);
        User u = mock(User.class);
        when(u.getUserId()).thenReturn(2L);
        when(other.getUser()).thenReturn(u);
        when(requestRepository.findByIdWithUser(10L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.getMyGpuUsage(10L, 1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("실행 중(FULFILLED)이 아닌 신청은 pod·node가 남아 있어도 조회하지 못한다")
    void rejectsNonFulfilled() {
        Request deleted = request(1L, "pod-old", "node-a");
        when(deleted.getStatus()).thenReturn(Status.DELETED);
        when(requestRepository.findByIdWithUser(10L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.getMyGpuUsage(10L, 1L)).isInstanceOf(BusinessException.class);
        org.mockito.Mockito.verifyNoInteractions(prometheus);
    }

    private void stubGpu(String uuid, String idx, String model, double util) {
        when(prometheus.querySeries(contains("DCGM_FI_DEV_GPU_UTIL"))).thenReturn(List.of(
                s(util, "UUID", uuid, "gpu", idx, "modelName", model)));
        when(prometheus.querySeries(contains("DCGM_FI_DEV_FB_USED"))).thenReturn(List.of(s(1, "UUID", uuid)));
        when(prometheus.querySeries(contains("DCGM_FI_DEV_FB_FREE"))).thenReturn(List.of(s(1, "UUID", uuid)));
    }

    @Test
    @DisplayName("프로세스 지표만 실패하면 GPU는 보이되 processesAvailable=false로 '모름'을 알린다")
    void processFailureIsFlagged() {
        Request r = request(1L, "p", "farm6");
        when(requestRepository.findByIdWithUser(10L)).thenReturn(Optional.of(r));
        stubGpu("GPU-1", "0", "NVIDIA RTX A5000", 3);
        when(prometheus.querySeries(contains("gpu_process_memory_used_bytes"))).thenThrow(new RuntimeException("down"));

        MyGpuUsageResponseDTO dto = service.getMyGpuUsage(10L, 1L);

        assertThat(dto.gpus()).hasSize(1);
        assertThat(dto.processesAvailable()).isFalse();
    }

    @Test
    @DisplayName("노드에 등록된 모델의 GPU만 보인다(표기 차이는 무시), 노드 정보가 없으면 전부 보인다")
    void filtersGpusByNodeModels() {
        Request r = request(1L, "p", "farm6");
        when(requestRepository.findByIdWithUser(10L)).thenReturn(Optional.of(r));
        when(prometheus.querySeries(contains("DCGM_FI_DEV_GPU_UTIL"))).thenReturn(List.of(
                s(1, "UUID", "GPU-1", "gpu", "0", "modelName", "NVIDIA GeForce RTX 3090"),
                s(1, "UUID", "GPU-2", "gpu", "1", "modelName", "NVIDIA RTX A5000")));
        Node node = mock(Node.class);
        when(node.getGpuModels()).thenReturn(List.of("RTX 3090"));
        when(nodeRepository.findByIdWithGpus("farm6")).thenReturn(Optional.of(node));

        assertThat(service.getMyGpuUsage(10L, 1L).gpus()).extracting(MyGpuUsageResponseDTO.Gpu::index).containsExactly(0);

        when(nodeRepository.findByIdWithGpus("farm6")).thenReturn(Optional.empty());
        MyGpuUsageService fresh = new MyGpuUsageService(prometheus, requestRepository, nodeRepository);
        assertThat(fresh.getMyGpuUsage(10L, 1L).gpus()).hasSize(2);
    }

    @Test
    @DisplayName("DCGM Hostname 선택자는 실제 라벨(farm6)에 대소문자·FQDN 차이를 허용하고 위험 문자는 이스케이프한다")
    void hostSelectorFormat() {
        assertThat(MyGpuUsageService.hostSelector("farm6")).isEqualTo("{Hostname=~\"(?i)farm6([.].*)?\"}");
        assertThat(MyGpuUsageService.hostSelector("a.b")).isEqualTo("{Hostname=~\"(?i)a[.]b([.].*)?\"}");
        assertThat(MyGpuUsageService.hostSelector("x\"y")).isEqualTo("{Hostname=\"x\\\"y\"}");
        assertThat(MyGpuUsageService.normalizeModel("NVIDIA GeForce RTX 3090")).isEqualTo("RTX 3090");
        assertThat(MyGpuUsageService.normalizeModel("RTX 3090 Ti")).isNotEqualTo("RTX 3090");
    }

    @Test
    @DisplayName("컨테이너 ID가 없는 프로세스는 PID별로 세고, 같은 컨테이너 ID 없는 것끼리 한 명으로 뭉치지 않는다")
    void missingContainerIdCountedByPid() {
        Request r = request(1L, "p", "farm6");
        when(requestRepository.findByIdWithUser(10L)).thenReturn(Optional.of(r));
        stubGpu("GPU-1", "0", "m", 1);
        when(prometheus.querySeries(contains("gpu_process_memory_used_bytes"))).thenReturn(List.of(
                s(1048576.0, "gpu_uuid", "GPU-1", "pid", "1"),
                s(1048576.0, "gpu_uuid", "GPU-1", "pid", "2"),
                s(1048576.0, "gpu_uuid", "GPU-1")));

        MyGpuUsageResponseDTO.Gpu g = service.getMyGpuUsage(10L, 1L).gpus().get(0);

        assertThat(g.otherUserCount()).isEqualTo(2);
        assertThat(g.otherMemoryMiB()).isEqualTo(3);
    }

    @Test
    @DisplayName("Docker 소유자는 같은 GPU 시계열일 때만 단축 ID로 맞춘다")
    void dockerOwnerMustMatchGpu() {
        Request r = request(1L, "p", "farm6");
        when(requestRepository.findByIdWithUser(10L)).thenReturn(Optional.of(r));
        stubGpu("GPU-1", "0", "m", 1);
        when(prometheus.querySeries(contains("gpu_process_memory_used_bytes"))).thenReturn(List.of(
                s(1048576.0, "gpu_uuid", "GPU-1", "container_id", C_DOCKER, "pid", "9")));
        // 같은 단축 ID이지만 다른 GPU의 시계열이면 소유자로 보지 않는다.
        when(prometheus.querySeries(contains("docker_gpu_user_container_info"))).thenReturn(List.of(
                s(1, "container_id", C_DOCKER.substring(0, 12), "gpu_uuid", "GPU-OTHER", "ubuntu_username", "u1")));

        MyGpuUsageResponseDTO.Gpu g = service.getMyGpuUsage(10L, 1L).gpus().get(0);

        assertThat(g.myProcesses()).isEmpty();
        assertThat(g.otherUserCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 노드를 짧은 시간에 다시 부르면 Prometheus를 다시 부르지 않는다")
    void cachesPerNode() {
        Request r = request(1L, "p", "farm6");
        when(requestRepository.findByIdWithUser(10L)).thenReturn(Optional.of(r));
        stubGpu("GPU-1", "0", "m", 1);

        service.getMyGpuUsage(10L, 1L);
        service.getMyGpuUsage(10L, 1L);

        org.mockito.Mockito.verify(prometheus, org.mockito.Mockito.times(1)).querySeries(contains("DCGM_FI_DEV_GPU_UTIL"));
    }

    @Test
    @DisplayName("Pod가 아직 없으면 빈 목록")
    void noPodMeansEmpty() {
        Request r = request(1L, null, null);
        when(requestRepository.findByIdWithUser(10L)).thenReturn(Optional.of(r));

        assertThat(service.getMyGpuUsage(10L, 1L).gpus()).isEmpty();
    }

    @Test
    @DisplayName("GPU 지표 조회가 실패하면 오류")
    void metricsFailureMeansEmpty() {
        Request r = request(1L, "p", "node-a");
        when(requestRepository.findByIdWithUser(10L)).thenReturn(Optional.of(r));
        when(prometheus.querySeries(contains("DCGM_FI_DEV_GPU_UTIL"))).thenThrow(new RuntimeException("down"));

        // 빈 목록('GPU 없음')으로 숨기지 않고 오류로 알린다.
        assertThatThrownBy(() -> service.getMyGpuUsage(10L, 1L)).isInstanceOf(BusinessException.class);
    }
}
