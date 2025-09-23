package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.portRequests.service.PortRequestService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ContainerInfoDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.PortMappingDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ResourceUsageDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
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
public class RequestQueryService {

    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final PortRequestService portRequestService;

    /** 내 신청 목록 */
    public List<SaveRequestResponseDTO> getRequestsByUserId(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return requestRepository.findAllByUser_UserId(userId).stream()
                .map(this::createResponseDTOWithPortMappings)
                .toList();
    }

    private SaveRequestResponseDTO createResponseDTOWithPortMappings(Request request) {
        List<PortMappingDTO> portMappings = portRequestService.getPortRequestsByRequestId(request.getRequestId())
                .stream()
                .map(PortMappingDTO::fromEntity)
                .toList();

        return SaveRequestResponseDTO.fromEntityWithPortMappings(request, portMappings);
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
        return requestRepository.findAllByUser_UserIdAndStatus(userId, Status.FULFILLED).stream()
                .map(this::createResponseDTOWithPortMappings)
                .toList();
    }

}
