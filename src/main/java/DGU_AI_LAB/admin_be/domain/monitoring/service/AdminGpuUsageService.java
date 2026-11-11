package DGU_AI_LAB.admin_be.domain.monitoring.service;

import DGU_AI_LAB.admin_be.domain.monitoring.client.PrometheusClient.Sample;
import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.AdminGpuUsageResponseDTO;
import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.AdminGpuUsageResponseDTO.Gpu;
import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.AdminGpuUsageResponseDTO.Process;
import DGU_AI_LAB.admin_be.domain.monitoring.service.MyGpuUsageService.Owner;
import DGU_AI_LAB.admin_be.domain.monitoring.service.MyGpuUsageService.Snapshot;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자용 노드별 GPU 사용 현황: GPU마다 프로세스와 소유자를 담는다.
 * Prometheus 조회·소유자 해석·짧은 캐시는 MyGpuUsageService의 것을 그대로 쓴다.
 * 사용자 응답과 달리 노드에 꽂힌 GPU를 모두 보인다(관리자 화면이라 거르지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminGpuUsageService {

    private final MyGpuUsageService myGpuUsageService;
    private final NodeRepository nodeRepository;

    /**
     * @param node null이면 DB의 모든 노드, 아니면 그 노드 하나(DB에 없으면 404).
     *             노드 하나를 지정했는데 GPU 지표를 못 읽으면 502, 전체 조회에서는 그 노드만 빈 GPU·processesAvailable=false로 둔다.
     */
    public List<AdminGpuUsageResponseDTO> getGpuUsage(String node) {
        if (node != null && !node.isBlank()) {
            if (!nodeRepository.existsById(node)) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            return List.of(usageOf(node, true));
        }
        return nodeRepository.findAll().stream()
                .map(Node::getNodeId)
                .sorted()
                .map(id -> usageOf(id, false))
                .toList();
    }

    private static Double ramMiB(Snapshot snap, String containerId) {
        Double bytes = snap.podRamBytesByContainer().get(containerId);
        return bytes == null ? null : bytes / MyGpuUsageService.MIB;
    }

    private AdminGpuUsageResponseDTO usageOf(String node, boolean failHard) {
        Snapshot snap;
        try {
            snap = myGpuUsageService.snapshot(node);
        } catch (Exception e) {
            log.warn("[ADMIN-GPU] GPU 지표 조회 실패: node={} {}", node, e.toString());
            if (failHard) throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
            return AdminGpuUsageResponseDTO.builder().nodeName(node).processesAvailable(false).gpus(List.of()).build();
        }

        Map<String, List<Process>> byGpu = new HashMap<>();
        if (snap.processesAvailable()) {
            for (Sample s : snap.procMem()) {
                String uuid = s.labels().get("gpu_uuid");
                String containerId = s.labels().get("container_id");
                Owner owner = MyGpuUsageService.ownerOf(snap, containerId, uuid);
                byGpu.computeIfAbsent(uuid, k -> new ArrayList<>()).add(Process.builder()
                        .ownerName(owner == null ? null : owner.name())
                        .ubuntuUsername(owner == null ? null : owner.ubuntuUsername())
                        .userId(owner == null ? null : owner.userId())
                        .requestId(owner == null ? null : owner.requestId())
                        .containerId(containerId == null || containerId.isBlank() ? null : MyGpuUsageService.shortId(containerId))
                        .containerRamMiB(containerId == null ? null : ramMiB(snap, containerId))
                        .processName(MyGpuUsageService.baseName(s.labels().get("process_name")))
                        .memoryUsedMiB(s.value() / MyGpuUsageService.MIB)
                        .elapsedSeconds(snap.elapsed().getOrDefault(MyGpuUsageService.procKey(s), 0.0).longValue())
                        .build());
            }
        }

        List<Gpu> gpus = snap.util().stream()
                .filter(s -> s.labels().get("UUID") != null)
                .map(s -> {
                    String uuid = s.labels().get("UUID");
                    double used = snap.used().containsKey(uuid) ? snap.used().get(uuid).value() : 0;
                    double free = snap.free().containsKey(uuid) ? snap.free().get(uuid).value() : 0;
                    List<Process> procs = new ArrayList<>(byGpu.getOrDefault(uuid, List.of()));
                    procs.sort(Comparator.comparingDouble(Process::memoryUsedMiB).reversed());
                    return Gpu.builder()
                            .index(MyGpuUsageService.parseIndex(s.labels().get("gpu")))
                            .uuid(uuid)
                            .model(s.labels().getOrDefault("modelName", ""))
                            .utilizationPercent(s.value())
                            .memoryUsedMiB(used)
                            .memoryTotalMiB(used + free)
                            .processes(procs)
                            .build();
                })
                .sorted(Comparator.comparingInt(Gpu::index))
                .toList();
        return AdminGpuUsageResponseDTO.builder().nodeName(node).processesAvailable(snap.processesAvailable()).gpus(gpus).build();
    }
}
