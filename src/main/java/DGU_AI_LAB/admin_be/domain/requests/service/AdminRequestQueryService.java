package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.pod.service.PodStatusCache;
import DGU_AI_LAB.admin_be.domain.pod.service.PodStatusSnapshot;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRequestQueryService {

    private final RequestRepository requestRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final RequestQueryService requestQueryService;
    private final PodStatusCache podStatusCache;

    /** 시작 시각을 주지 않았을 때 쓰는 하한 — 어떤 신청보다도 앞선 시각이다. */
    private static final LocalDateTime EARLIEST = LocalDateTime.of(1970, 1, 1, 0, 0);

    /** 작업 기록을 이 신청의 것으로만 거르는 기준 시각. */
    public LocalDateTime getRequestCreatedAt(Long requestId) {
        return requestRepository.findById(requestId)
                .map(Request::getCreatedAt)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 신청 목록. 상태를 주지 않으면 모든 상태, 시작 시각을 주지 않으면 전체 기간이다.
     */
    public List<SaveRequestResponseDTO> getRequests(Collection<Status> statuses, LocalDateTime createdFrom) {
        Collection<Status> wanted = statuses == null || statuses.isEmpty() ? EnumSet.allOf(Status.class) : statuses;
        List<Request> requests = requestRepository.findAllForAdminList(wanted, orEarliest(createdFrom));
        return requestQueryService.toResponseDTOs(requests);
    }

    public SaveRequestResponseDTO getRequest(Long requestId) {
        Request request = requestRepository.findByIdForAdminView(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return requestQueryService.toResponseDTOs(List.of(request)).get(0);
    }

    /** 상태별 신청 건수. 화면이 탭마다 0을 따로 채우지 않도록 건수가 없는 상태도 0으로 넣는다. */
    public Map<Status, Long> getRequestStatusCounts(LocalDateTime createdFrom) {
        Map<Status, Long> counts = new EnumMap<>(Status.class);
        for (Status status : Status.values()) {
            counts.put(status, 0L);
        }
        requestRepository.countByStatusCreatedFrom(orEarliest(createdFrom))
                .forEach(row -> counts.put(row.getStatus(), row.getCount()));
        return counts;
    }

    private static LocalDateTime orEarliest(LocalDateTime createdFrom) {
        return createdFrom == null ? EARLIEST : createdFrom;
    }

    public List<ResourceUsageDTO> getAllFulfilledResourceUsage() {
        return requestRepository.findAllByStatusInWithAssociations(Status.activeStatuses()).stream()
                .map(ResourceUsageDTO::fromEntity)
                .toList();
    }

    /** Pod 상태는 k8s를 부르지 않고 주기 작업이 읽어 둔 값을 붙인다. */
    public List<ContainerInfoDTO> getAllActiveContainers() {
        PodStatusSnapshot snapshot = podStatusCache.snapshot();
        return requestRepository.findAllByStatusInWithAssociations(Status.activeStatuses()).stream()
                .map(request -> ContainerInfoDTO.fromEntity(
                        request, snapshot.pod(request.getPodName()), snapshot.checkedAt()))
                .toList();
    }

    public List<ChangeRequestResponseDTO> getAllChangeRequests() {
        return changeRequestRepository.findAllByStatusNot(Status.DELETED).stream()
                .map(ChangeRequestResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
