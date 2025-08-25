package DGU_AI_LAB.admin_be.domain.dashboard.service;

import DGU_AI_LAB.admin_be.domain.containerImage.dto.response.ContainerImageResponseDTO;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.nodes.repository.NodeRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.UserServerResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    public List<UserServerResponseDTO> getUserServers(Long userId, Status status) {
        log.info("[getUserServers] userId={}의 status={} 서버 목록 조회 시작", userId, status);

        List<Request> requests;
        if (status == Status.ALL) {
            requests = requestRepository.findAllByUser_UserId(userId);
        } else {
            requests = requestRepository.findByUserUserIdAndStatus(userId, status);
        }

        return requests.stream()
                .map(request -> {
                    String serverAddress = (request.getStatus() == Status.FULFILLED) ? "TBD (서버 할당 후 표시)" : null;

                    Integer cpuCoreCount = null;
                    Integer memoryGB = null;
                    String resourceGroupName = null;

                    ResourceGroup resourceGroup = request.getResourceGroup();
                    if (resourceGroup != null) {
                        resourceGroupName = resourceGroup.getDescription();

                        List<Node> nodesInGroup = nodeRepository.findAllByResourceGroup(resourceGroup);
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