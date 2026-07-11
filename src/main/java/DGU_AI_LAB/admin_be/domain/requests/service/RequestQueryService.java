package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.PodExternalPortResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.PortMappingDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.ChangeRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestQueryService {

    private final RequestRepository requestRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final UserRepository userRepository;
    private final PortRequestRepository portRequestRepository;
    private final PodExternalPortRepository podExternalPortRepository;

    /** 내 신청 목록 */
    public List<SaveRequestResponseDTO> getRequestsByUserId(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toResponseDTOs(requestRepository.findAllByUser_UserId(userId));
    }

    /** 목록 단위 배치 로딩으로 N+1 방지: portRequests, podExternalPorts를 IN절 1회씩 조회 */
    private List<SaveRequestResponseDTO> toResponseDTOs(List<Request> requests) {
        if (requests.isEmpty()) return List.of();

        List<Long> ids = requests.stream().map(Request::getRequestId).toList();

        Map<Long, List<PortMappingDTO>> portMappingsByRequest =
                portRequestRepository.findByRequestRequestIdIn(ids).stream()
                        .collect(Collectors.groupingBy(
                                pr -> pr.getRequest().getRequestId(),
                                Collectors.mapping(PortMappingDTO::fromEntity, Collectors.toList())
                        ));

        Map<Long, List<PodExternalPortResponseDTO>> podPortsByRequest =
                podExternalPortRepository.findByRequestRequestIdIn(ids).stream()
                        .collect(Collectors.groupingBy(
                                pep -> pep.getRequest().getRequestId(),
                                Collectors.mapping(PodExternalPortResponseDTO::fromEntity, Collectors.toList())
                        ));

        return requests.stream()
                .map(r -> SaveRequestResponseDTO.fromEntityWithPorts(
                        r,
                        portMappingsByRequest.getOrDefault(r.getRequestId(), List.of()),
                        podPortsByRequest.getOrDefault(r.getRequestId(), List.of())
                ))
                .toList();
    }

    /** 승인 완료 자원 사용량 */
    public List<ResourceUsageDTO> getAllFulfilledResourceUsage() {
        return requestRepository.findAllByStatus(Status.FULFILLED).stream()
                .map(ResourceUsageDTO::fromEntity)
                .toList();
    }

    /** 활성 컨테이너 */
    public List<ContainerInfoDTO> getActiveContainers() {
        return requestRepository.findAllByStatus(Status.FULFILLED).stream()
                .map(ContainerInfoDTO::fromEntity)
                .toList();
    }


    /**
     * 승인 완료(FULFILLED)된 모든 요청의 ubuntuUsername 목록을 조회합니다.
     */
    public List<String> getAllFulfilledUsernames() {
        return requestRepository.findUbuntuUsernamesByStatus(Status.FULFILLED);
    }

    /** 내 신청 목록 중 승인 완료(FULFILLED) 상태만 조회 */
    public List<SaveRequestResponseDTO> getApprovedRequestsByUserId(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toResponseDTOs(requestRepository.findAllByUser_UserIdAndStatus(userId, Status.FULFILLED));
    }

    /** 내 변경 요청 목록 조회 */
    public List<ChangeRequestResponseDTO> getMyChangeRequests(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return changeRequestRepository.findAllByRequestedBy_UserId(userId).stream()
                .map(ChangeRequestResponseDTO::fromEntity)
                .toList();
    }

}
