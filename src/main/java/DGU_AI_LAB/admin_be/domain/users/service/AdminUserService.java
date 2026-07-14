package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.UbuntuAccountService;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.ConflictException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final UbuntuAccountService ubuntuAccountService;

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

        for (Request request : userRequests) {
            if (request.getStatus() == Status.FULFILLED) {
                ubuntuAccountService.deleteUbuntuAccount(request.getUbuntuUsername());
                request.deleteAfterCleanup();
                requestRepository.save(request);
            } else if (request.getStatus() != Status.DELETED) {
                request.delete();
            }
        }
        log.info("[deleteUser] userId={}와 연결된 Request 정리 완료", userId);

        user.updateUserInfo(null, false);
        log.info("[deleteUser] userId={} 논리적 삭제 완료 (isActive=false)", userId);
    }

    /**
     * 단독 우분투 계정 삭제 (컨트롤러 엔드포인트용)
     * FULFILLED 상태인 Request를 username으로 찾아 외부 계정 삭제 후 DB 상태를 DELETED로 변경한다.
     */
    @Transactional
    public void deleteUbuntuAccount(String username) {
        log.warn("[deleteUbuntuAccount] 우분투 계정 삭제 시도: {}", username);
        Request request = requestRepository.findByUbuntuUsername(username)
                .orElseThrow(() -> {
                    log.warn("[deleteUbuntuAccount] {}에 해당하는 Request가 없습니다.", username);
                    return new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND);
                });

        if (request.getStatus() == Status.DELETED) {
            log.warn("[deleteUbuntuAccount] {}은 이미 DELETED 상태입니다.", username);
            throw new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND);
        }

        ubuntuAccountService.deleteUbuntuAccount(username);
        request.deleteAfterCleanup();
        requestRepository.save(request);
        log.info("[deleteUbuntuAccount] {} 계정 삭제 및 DB 상태 업데이트 완료", username);
    }

    /**
     * 비활성화된 유저 재활성화
     */
    @Transactional
    public UserSummaryDTO reactivateUser(Long userId) {
        log.info("[reactivateUser] userId={} 재활성화 시도", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

        if (user.getIsActive()) {
            log.warn("[reactivateUser] userId={} 이미 활성화 상태", userId);
            throw new ConflictException(ErrorCode.USER_ALREADY_ACTIVE);
        }

        user.reactivate();
        log.info("[reactivateUser] userId={} 재활성화 완료", userId);
        return UserSummaryDTO.fromEntity(user);
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
}
