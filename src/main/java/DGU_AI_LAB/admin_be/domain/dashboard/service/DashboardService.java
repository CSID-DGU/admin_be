package DGU_AI_LAB.admin_be.domain.dashboard.service;

import DGU_AI_LAB.admin_be.domain.containerImage.dto.response.ContainerImageResponseDTO;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.UserServerResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.entity.StatusFilter;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final RequestRepository requestRepository;
    private final NodeRepository nodeRepository;

    /**
     * 사용자 대시보드 서버 목록 조회
     * 승인받은 서버 및 승인 대기중인 신청 목록을 필터링하여 반환합니다.
     */
    public List<UserServerResponseDTO> getUserServers(Long userId, StatusFilter statusFilter) {
        log.info("[getUserServers] userId={}의 statusFilter={} 서버 목록 조회 시작", userId, statusFilter);

        List<Request> requests;
        if (statusFilter == StatusFilter.ALL) {
            requests = requestRepository.findAllByUser_UserId(userId);
        } else {
            Status status = Status.valueOf(statusFilter.name());
            requests = requestRepository.findByUserUserIdAndStatus(userId, status);
        }

        Set<ResourceGroup> resourceGroups = requests.stream()
                .map(Request::getResourceGroup)
                .filter(rg -> rg != null)
                .collect(Collectors.toSet());

        Map<Integer, List<Node>> nodesByRsgroupId = nodeRepository
                .findAllByResourceGroupIn(resourceGroups)
                .stream()
                .collect(Collectors.groupingBy(node -> node.getResourceGroup().getRsgroupId()));

        return requests.stream()
                .map(request -> {
                    String serverAddress = (request.getStatus() == Status.FULFILLED) ? "TBD (서버 할당 후 표시)" : null;

                    Integer cpuCoreCount = null;
                    Integer memoryGB = null;
                    String resourceGroupName = null;

                    ResourceGroup resourceGroup = request.getResourceGroup();
                    if (resourceGroup != null) {
                        resourceGroupName = resourceGroup.getDescription();

                        List<Node> nodesInGroup = nodesByRsgroupId.getOrDefault(resourceGroup.getRsgroupId(), List.of());
                        if (!nodesInGroup.isEmpty()) {
                            Node representativeNode = nodesInGroup.get(0);
                            cpuCoreCount = representativeNode.getCpuCoreCount();
                            memoryGB = representativeNode.getMemorySizeGB();
                        }
                    }

                    ContainerImageResponseDTO containerImageDTO = null;
                    if (request.getContainerImage() != null) {
                        containerImageDTO = ContainerImageResponseDTO.fromEntity(request.getContainerImage());
                    }

                    return UserServerResponseDTO.fromEntity(
                            request,
                            serverAddress,
                            cpuCoreCount,
                            memoryGB,
                            resourceGroupName,
                            containerImageDTO
                    );
                })
                .collect(Collectors.toList());
    }
}