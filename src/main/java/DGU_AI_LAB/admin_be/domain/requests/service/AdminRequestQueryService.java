package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.PodExternalPortResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.PortMappingDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRequestQueryService {

    private final RequestRepository requestRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final PortRequestRepository portRequestRepository;
    private final PodExternalPortRepository podExternalPortRepository;

    public List<SaveRequestResponseDTO> getAllRequests() {
        List<Request> requests = requestRepository.findAll();
        return buildResponseDTOs(requests);
    }

    public List<SaveRequestResponseDTO> getNewRequests() {
        List<Request> requests = requestRepository.findAllByStatus(Status.PENDING);
        return buildResponseDTOs(requests);
    }

    private List<SaveRequestResponseDTO> buildResponseDTOs(List<Request> requests) {
        if (requests.isEmpty()) return List.of();

        List<Long> requestIds = requests.stream().map(Request::getRequestId).toList();

        Map<Long, List<PortMappingDTO>> portMappingsByRequestId = portRequestRepository
                .findByRequestRequestIdIn(requestIds)
                .stream()
                .collect(Collectors.groupingBy(
                        pr -> pr.getRequest().getRequestId(),
                        Collectors.mapping(PortMappingDTO::fromEntity, Collectors.toList())
                ));

        Map<Long, List<PodExternalPortResponseDTO>> podPortsByRequestId = podExternalPortRepository
                .findByRequestRequestIdIn(requestIds)
                .stream()
                .collect(Collectors.groupingBy(
                        pp -> pp.getRequest().getRequestId(),
                        Collectors.mapping(PodExternalPortResponseDTO::fromEntity, Collectors.toList())
                ));

        return requests.stream()
                .map(request -> SaveRequestResponseDTO.fromEntityWithPorts(
                        request,
                        portMappingsByRequestId.getOrDefault(request.getRequestId(), List.of()),
                        podPortsByRequestId.getOrDefault(request.getRequestId(), List.of())
                ))
                .toList();
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
