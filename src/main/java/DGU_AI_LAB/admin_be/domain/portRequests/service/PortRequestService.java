package DGU_AI_LAB.admin_be.domain.portRequests.service;

import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortRequestService {

    private final PortRequestRepository portRequestRepository;

    private static final int PORT_RANGE_START = 10000;
    private static final int PORT_RANGE_END = 20000;

    @Transactional
    public PortRequests createPortRequest(Request request, ResourceGroup resourceGroup,
                                        Integer internalPort, String usagePurpose) {

        // Auto-assign external port number from range 10000-20000
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

        // Find the first available port in range 10000-20000
        for (int port = PORT_RANGE_START; port <= PORT_RANGE_END; port++) {
            if (!usedPorts.contains(port)) {
                return port;
            }
        }

        // No available ports in range
        return null;
    }

    public List<PortRequests> getPortRequestsByRequestId(Long requestId) {
        return portRequestRepository.findByRequestRequestId(requestId);
    }

    public List<PortRequests> getPortRequestsByResourceGroupId(Integer resourceGroupId) {
        return portRequestRepository.findByResourceGroupRsgroupId(resourceGroupId);
    }

    @Transactional
    public void activatePortRequest(Long portRequestId) {
        PortRequests portRequest = portRequestRepository.findById(portRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        log.info("Port request {} activated", portRequestId);
    }
}