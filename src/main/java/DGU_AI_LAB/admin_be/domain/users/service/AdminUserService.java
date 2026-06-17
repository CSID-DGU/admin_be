package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.PodService;
import DGU_AI_LAB.admin_be.domain.requests.service.UbuntuAccountService;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.event.PodCleanupFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final PodService podService;
    private final UbuntuAccountService ubuntuAccountService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 전체 유저 조회
     */
    @Transactional(readOnly = true)
    public List<UserSummaryDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserSummaryDTO::fromEntity)
                .toList();
    }

    /**
     * 유저 삭제 (soft delete 적용)
     * 이와 동시에 해당 유저가 소유한 모든 Request(우분투 계정)의 상태를 'DELETED'로 변경하고 외부 시스템에서도 삭제합니다.
     */
    @Transactional
    public void deleteUser(Long userId) {
        log.warn("[deleteUser] userId={} 논리적 삭제 시도", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("[deleteUser] userId={} 존재하지 않음", userId);
                    return new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND);
                });

        List<Request> userRequests = requestRepository.findAllByUser(user);

        if (!userRequests.isEmpty()) {
            for (Request request : userRequests) {
                if (request.getStatus() != Status.FULFILLED) continue;
                deleteRequestResources(request);
            }
            log.info("[deleteUser] userId={}와 연결된 모든 Request의 외부 리소스 삭제 완료", userId);
        } else {
            log.info("[deleteUser] userId={}와 연결된 Request가 없습니다. 외부 리소스 삭제를 건너뜁니다.", userId);
        }

        user.updateUserInfo(null, false);
        log.info("[deleteUser] userId={} 논리적 삭제 완료 (isActive=false)", userId);
    }

    /**
     * 유저 정보 수정
     */
    @Transactional
    public UserResponseDTO updateUser(Long userId, UserUpdateRequestDTO request) {
        log.info("[updateUser] userId={} 정보 수정 시작", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
        user.updateUserInfo(request.password(), request.isActive());
        log.info("[updateUser] userId={} 정보 수정 완료", userId);
        return UserResponseDTO.fromEntity(user);
    }

    /**
     * username으로 해당 Request의 인프라 리소스를 삭제합니다.
     * 컨트롤러 엔드포인트 DELETE /api/admin/users/ubuntu/{username} 에서 호출.
     */
    @Transactional
    public void deleteUbuntuAccount(String username) {
        Request request = requestRepository.findByUbuntuUsername(username)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
        if (request.getStatus() != Status.FULFILLED) return;
        deleteRequestResources(request);
    }

    /**
     * Request에 연결된 모든 인프라 리소스를 삭제합니다.
     * 순서: Pod 삭제 >> PVC/계정 삭제 >> DB 상태 변경
     */
    private void deleteRequestResources(Request request) {
        String username = request.getUbuntuUsername();
        log.warn("[deleteRequestResources] 리소스 삭제 시도: username={}, pod={}", username, request.getPodName());

        boolean podCleanupFailed = false;
        try {
            podService.deletePod(request.getPodName());
        } catch (Exception e) {
            log.error("[deleteRequestResources] Pod 삭제 실패 - 자동 재시도 등록 예정: pod={}", request.getPodName(), e);
            podCleanupFailed = true;
        }

        ubuntuAccountService.deleteUbuntuAccount(username);

        request.delete();

        if (podCleanupFailed) {
            eventPublisher.publishEvent(new PodCleanupFailedEvent(request.getPodName(), username));
        }

        log.info("[deleteRequestResources] {} 리소스 삭제 및 상태 DELETED 변경 완료", username);
    }
}