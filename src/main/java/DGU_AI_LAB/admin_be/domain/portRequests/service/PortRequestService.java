package DGU_AI_LAB.admin_be.domain.portRequests.service;

import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
