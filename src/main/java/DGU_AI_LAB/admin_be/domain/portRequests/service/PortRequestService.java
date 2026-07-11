package DGU_AI_LAB.admin_be.domain.portRequests.service;

import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortRequestService {

    private final PortRequestRepository portRequestRepository;
    private final ResourceGroupRepository resourceGroupRepository;

    private static final int NODE_PORT_RANGE_START = 30000;
    private static final int NODE_PORT_RANGE_END = 32767;

    @Transactional
    public PortRequests createPortRequest(Request request, ResourceGroup resourceGroup,
                                        Integer internalPort, String usagePurpose) {

        // 포트 할당 Race Condition 방지: ResourceGroup 행에 비관적 락을 걸어 직렬화
        resourceGroupRepository.findByIdWithPessimisticLock(resourceGroup.getRsgroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // Auto-assign external NodePort from the Kubernetes default range.
        Integer assignedPortNumber = findNextAvailablePort(resourceGroup.getRsgroupId());

        if (assignedPortNumber == null) {
            throw new BusinessException(ErrorCode.NO_AVAILABLE_PORT);
        }

        PortRequests portRequest = PortRequests.builder()
                .request(request)
                .resourceGroup(resourceGroup)
                .portNumber(assignedPortNumber)
                .internalPort(internalPort)
                .usagePurpose(usagePurpose)
                .build();

        return portRequestRepository.save(portRequest);
    }

    private Integer findNextAvailablePort(Integer resourceGroupId) {
        // Get all used port numbers in ascending order
        List<Integer> usedPorts = portRequestRepository.findPortNumbersByResourceGroupRsgroupIdOrderByPortNumberAsc(resourceGroupId);
        Set<Integer> usedPortSet = Set.copyOf(usedPorts);

        // Find the first available port in range 30000-32767.
        for (int port = NODE_PORT_RANGE_START; port <= NODE_PORT_RANGE_END; port++) {
            if (!usedPortSet.contains(port)) {
                return port;
            }
        }

        return null;
    }

    public List<PortRequests> getPortRequestsByRequestId(Long requestId) {
        return portRequestRepository.findByRequestRequestId(requestId);
    }

    public List<PortRequests> getPortRequestsByResourceGroupId(Integer resourceGroupId) {
        return portRequestRepository.findByResourceGroupRsgroupId(resourceGroupId);
    }

    @Transactional
    public PortRequests savePort(Request request, ResourceGroup resourceGroup,
                                 Integer internalPort, Integer externalPort, String usagePurpose) {
        PortRequests portRequest = PortRequests.builder()
                .request(request)
                .resourceGroup(resourceGroup)
                .portNumber(externalPort)
                .internalPort(internalPort)
                .usagePurpose(usagePurpose)
                .build();
        return portRequestRepository.save(portRequest);
    }

    @Transactional
    public void activatePortRequest(Long portRequestId) {
        PortRequests portRequest = portRequestRepository.findById(portRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        portRequest.activate();
        log.info("Port request {} activated", portRequestId);
    }
}