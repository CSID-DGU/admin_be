package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.pod.entity.PodExternalPort;
import DGU_AI_LAB.admin_be.domain.pod.repository.PodExternalPortRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.PodService;
import DGU_AI_LAB.admin_be.domain.requests.service.UbuntuAccountService;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.ConflictException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final UbuntuAccountService ubuntuAccountService;
    private final PodService podService;
    private final AlarmService alarmService;
    private final PodExternalPortRepository podExternalPortRepository;
    private final MessageUtils messageUtils;
    private final TokenService tokenService;
    private final PlatformTransactionManager transactionManager;

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
     * 유저가 소유한 모든 Request(우분투 계정/컨테이너)를 정리한다.
     * FULFILLED 상태는 외부 계정/Pod를 삭제하고, 나머지는 논리 삭제한다.
     * MIGRATING 중인 요청이 하나라도 있으면 정리 자체를 거부한다 — 마이그레이션 결과가
     * 반영될 Request의 소유자가 이미 정리된 상태로 남아 정합성이 깨지는 것을 막기 위함이다.
     * deleteUser(완전 탈퇴)와 deactivateUser(soft-delete와 동일 효과의 임시 비활성화)가
     * 이 정리 로직을 공유한다.
     *
     * 요청 하나마다 REQUIRES_NEW로 독립 커밋한다 — 이 메서드는 호출자(deleteUser/
     * deactivateUser)의 @Transactional 안에서 실행되는데, 그 트랜잭션 하나로 묶여 있으면
     * 사용자가 컨테이너를 여러 개 가진 경우 뒤쪽 요청의 Pod/계정 삭제가 실패했을 때 이미
     * 물리적으로 삭제된(롤백 불가능한) 앞쪽 요청들의 DB 반영까지 통째로 롤백돼버려서, DB엔
     * FULFILLED로 남아있는데 실제 Pod/계정은 사라진 고아 레코드가 생긴다.
     */
    private void cleanupUserRequests(User user, String logPrefix) {
        List<Request> userRequests = requestRepository.findAllByUser(user);

        // MIGRATING뿐 아니라 PROCESSING(승인 처리 중)도 막는다 — 승인 트랜잭션이 진행 중인
        // 사이에 요청이 delete()로 넘어가면, 그 승인이 나중에 완료될 때 이미 소유자가 정리된
        // Request를 FULFILLED로 덮어써 정합성이 깨진다.
        boolean hasInFlightRequest = userRequests.stream()
                .anyMatch(r -> r.getStatus() == Status.MIGRATING || r.getStatus() == Status.PROCESSING);
        if (hasInFlightRequest) {
            log.warn("[{}] userId={} 승인/마이그레이션 진행 중인 요청이 있어 정리를 거부합니다.", logPrefix, user.getUserId());
            throw new ConflictException(ErrorCode.REQUEST_MIGRATION_IN_PROGRESS);
        }

        List<Long> fulfilledIds = userRequests.stream()
                .filter(r -> r.getStatus() == Status.FULFILLED)
                .map(Request::getRequestId)
                .collect(Collectors.toList());
        Map<Long, List<PodExternalPort>> portsMap = fulfilledIds.isEmpty()
                ? Map.of()
                : podExternalPortRepository
                        .findByRequestRequestIdIn(fulfilledIds)
                        .stream()
                        .collect(Collectors.groupingBy(p -> p.getRequest().getRequestId()));

        TransactionTemplate newTx = new TransactionTemplate(transactionManager);
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        List<Long> failedRequestIds = new ArrayList<>();
        for (Request request : userRequests) {
            Long requestId = request.getRequestId();
            if (fulfilledIds.contains(requestId)) {
                try {
                    podService.deletePod(request.getPodName());
                    ubuntuAccountService.deleteUbuntuAccount(request.getUbuntuUsername(), request.getNodeName());
                } catch (Exception e) {
                    log.error("[{}] userId={} requestId={} Pod/계정 삭제 실패 — 이 요청은 FULFILLED로 남기고 다음 요청을 계속 정리합니다: {}",
                            logPrefix, user.getUserId(), requestId, e.getMessage());
                    failedRequestIds.add(requestId);
                    try {
                        alarmService.sendSlackAlert(String.format(
                                "[%s] userId=%d 정리 중 Pod/계정 삭제 실패 - 수동 확인 필요: requestId=%d, ubuntuUsername=%s",
                                logPrefix, user.getUserId(), requestId, request.getUbuntuUsername()), null);
                    } catch (Exception ignored) {
                        // 알림 발송 실패가 다른 요청 정리를 막으면 안 된다.
                    }
                    continue;
                }
                newTx.execute(status -> {
                    Request managed = requestRepository.findById(requestId)
                            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
                    managed.deleteAfterCleanup();
                    return null;
                });
                try {
                    List<PodExternalPort> ports = portsMap.getOrDefault(requestId, List.of());
                    alarmService.sendContainerDeletedEmail(request, ports);
                } catch (Exception e) {
                    log.warn("[{}] 삭제 안내 메일 발송 실패: ubuntuUsername={}", logPrefix, request.getUbuntuUsername(), e);
                }
            } else if (request.getStatus() != Status.DELETED) {
                newTx.execute(status -> {
                    Request managed = requestRepository.findById(requestId)
                            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
                    managed.delete();
                    return null;
                });
            }
        }

        if (!failedRequestIds.isEmpty()) {
            throw new BusinessException(ErrorCode.USER_REQUEST_CLEANUP_PARTIALLY_FAILED);
        }
        log.info("[{}] userId={}와 연결된 Request 정리 완료", logPrefix, user.getUserId());
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

        cleanupUserRequests(user, "deleteUser");

        user.withdraw();
        // 남아있는 리프레시 토큰으로 액세스 토큰을 계속 재발급받지 못하도록 함께 폐기한다.
        tokenService.logout(userId);
        log.info("[deleteUser] userId={} 논리적 삭제 완료 (isActive=false)", userId);

        try {
            String subject = messageUtils.get("notification.user.admin-delete.subject");
            String body = messageUtils.get("notification.user.admin-delete.body", user.getName());
            alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, body);
        } catch (Exception e) {
            log.warn("[deleteUser] 계정 비활성화 안내 메일 발송 실패: userId={}", userId, e);
        }
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

        podService.deletePod(request.getPodName());
        ubuntuAccountService.deleteUbuntuAccount(username, request.getNodeName());
        request.deleteAfterCleanup();
        requestRepository.save(request);
        log.info("[deleteUbuntuAccount] {} 계정 삭제 및 DB 상태 업데이트 완료", username);
        try {
            alarmService.sendContainerDeletedEmail(request);
        } catch (Exception e) {
            log.warn("[deleteUbuntuAccount] 삭제 안내 메일 발송 실패: ubuntuUsername={}", username, e);
        }
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
     * 유저 임시 비활성화. deleteUser(withdraw)와 동일하게 소유한 모든 Request의 우분투
     * 계정/Pod를 정리하지만(soft-delete와 동일한 효과), User 엔티티 자체는 withdraw()가 아닌
     * deactivate()로 처리해 deletedAt을 남기지 않는다 — reactivateUser로 로그인만 되돌릴 수
     * 있으며, 컨테이너는 이미 정리되었으므로 사용자가 다시 신청해야 한다.
     */
    @Transactional
    public UserSummaryDTO deactivateUser(Long userId) {
        log.info("[deactivateUser] userId={} 비활성화 시도", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

        if (!user.getIsActive()) {
            log.warn("[deactivateUser] userId={} 이미 비활성화 상태", userId);
            throw new ConflictException(ErrorCode.USER_ALREADY_INACTIVE);
        }

        cleanupUserRequests(user, "deactivateUser");

        user.deactivate();
        tokenService.logout(userId);
        log.info("[deactivateUser] userId={} 비활성화 완료 (컨테이너 정리 포함)", userId);

        try {
            String subject = messageUtils.get("notification.user.admin-delete.subject");
            String body = messageUtils.get("notification.user.admin-delete.body", user.getName());
            alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, body);
        } catch (Exception e) {
            log.warn("[deactivateUser] 계정 비활성화 안내 메일 발송 실패: userId={}", userId, e);
        }

        return UserSummaryDTO.fromEntity(user);
    }

    /**
     * 유저 권한 변경 (ADMIN <-> USER)
     */
    @Transactional
    public UserSummaryDTO changeUserRole(Long userId, Role newRole) {
        log.info("[changeUserRole] userId={} role={} 변경 시도", userId, newRole);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() == newRole) {
            log.warn("[changeUserRole] userId={} 이미 {} 권한", userId, newRole);
            throw new ConflictException(ErrorCode.USER_ALREADY_HAS_ROLE);
        }

        user.changeRole(newRole);
        log.info("[changeUserRole] userId={} role={} 변경 완료", userId, newRole);
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
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            // 비활성화 처리된 계정은 리프레시 토큰도 함께 폐기한다.
            tokenService.logout(userId);
        }
        log.info("[updateUser] userId={} 정보 수정 완료", userId);
        return UserResponseDTO.fromEntity(user);
    }
}
