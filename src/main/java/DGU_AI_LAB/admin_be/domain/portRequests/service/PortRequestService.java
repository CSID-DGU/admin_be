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

    @Transactional
    public PortRequests createPortRequest(Request request, ResourceGroup resourceGroup,
                                          Integer internalPort, String usagePurpose) {
        PortRequests portRequest = PortRequests.builder()
                .request(request)
                .resourceGroup(resourceGroup)
                .internalPort(internalPort)
                .usagePurpose(usagePurpose)
                .build();

        return portRequestRepository.save(portRequest);
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

        portRequest.activate();
        log.info("Port request {} activated", portRequestId);
    }
}
