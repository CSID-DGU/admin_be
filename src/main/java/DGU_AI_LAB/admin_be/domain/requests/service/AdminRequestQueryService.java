package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.PortMappingDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRequestQueryService {

    private final RequestRepository requestRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final PortRequestService portRequestService;

    public List<SaveRequestResponseDTO> getAllRequests() {
        return requestRepository.findAll().stream()
                .map(this::createResponseDTOWithPortMappings)
                .collect(Collectors.toList());
    }

    public List<SaveRequestResponseDTO> getNewRequests() {
        return requestRepository.findAllByStatus(Status.PENDING).stream()
                .map(this::createResponseDTOWithPortMappings)
                .collect(Collectors.toList());
    }

    private SaveRequestResponseDTO createResponseDTOWithPortMappings(Request request) {
        List<PortMappingDTO> portMappings = portRequestService.getPortRequestsByRequestId(request.getRequestId())
                .stream()
                .map(PortMappingDTO::fromEntity)
                .toList();

        return SaveRequestResponseDTO.fromEntityWithPortMappings(request, portMappings);
    }

    public List<ResourceUsageDTO> getAllFulfilledResourceUsage() {
        return requestRepository.findAllByStatus(Status.FULFILLED).stream()
                .map(ResourceUsageDTO::fromEntity)
                .toList();
    }

    public List<ContainerInfoDTO> getAllActiveContainers() {
        return requestRepository.findAllByStatus(Status.FULFILLED).stream()
                .map(ContainerInfoDTO::fromEntity)
                .toList();
    }

    public List<ChangeRequestResponseDTO> getChangeRequests() {
        return changeRequestRepository.findAllByStatus(Status.PENDING).stream()
                .map(ChangeRequestResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ChangeRequestResponseDTO> getAllChangeRequests() {
        return changeRequestRepository.findAll().stream()
                .map(ChangeRequestResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}