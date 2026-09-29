package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.service.PodMigrationService;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestExpiryService;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserResponseDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.UbuntuAccountStatus;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final RequestExpiryService requestExpiryService;
    private final PodMigrationService podMigrationService;
    private final AlarmService alarmService;
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
     * 유저가 소유한 모든 Request(컨테이너)의 회수를 시작하고, 우분투 계정을 회수 중(RELEASING)으로 바꾼다.
     * 결과를 기다리지 않는다 — FULFILLED 신청은 컨테이너 회수 작업을 등록해 EXPIRING이 되고(RevokeJobPoller가
     * DELETED로 마무리), 나머지는 논리 삭제한다. 계정은 컨테이너 회수가 모두 끝난 뒤 AccountRevokeJobPoller가
     * 노드마다 회수한다. deleteUser(완전 탈퇴)·deactivateUser(임시 비활성화)·deleteUbuntuAccount가 공유한다.
     *
     * <p>MIGRATING·PROCESSING 신청이 있으면 아무것도 건드리지 않고 거부한다 — 그 작업의 결과가 반영될 신청의
     * 소유자가 이미 정리된 상태로 남는다. EXPIRING은 이미 회수 중이라 그대로 두고 기다린다.
     *
     * <p>회수 중인 계정에 다시 부르면(관리자의 재시도) 실패한 컨테이너 회수와 모든 노드의 계정 회수를 다시 등록한다.
     */
    private void cleanupUserRequests(User user, String logPrefix) {
        TransactionTemplate newTx = new TransactionTemplate(transactionManager);
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Long userId = user.getUserId();

        List<Request> userRequests = newTx.execute(status -> requestRepository.findAllByUser(user));

        // 마이그레이션이 방금 끝났는데 아직 상태가 되돌아오지 않았을 수 있다(결과 반영은 주기 작업이
        // 한다). 그 틈 때문에 정리가 통째로 거부되지 않도록, 검사 전에 끝난 결과를 먼저 반영한다.
        if (userRequests.stream().anyMatch(r -> r.getStatus() == Status.MIGRATING)) {
            userRequests.stream()
                    .filter(r -> r.getStatus() == Status.MIGRATING)
                    .forEach(r -> podMigrationService.settleFinishedMigration(r.getRequestId()));
        }

        // 검사와 계정 상태 전환을 사용자 행 잠금 안에서 한다 — 승인도 이 행을 잠그고 계정 상태를 읽으므로,
        // RELEASING이 커밋된 뒤 시작되는 승인은 거절되고, 그 전에 시작된 승인은 여기서 PROCESSING으로 보인다.
        List<Request> targets = newTx.execute(status -> {
            User managed = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
            List<Request> current = requestRepository.findAllByUser(managed);
            boolean hasInFlightRequest = current.stream()
                    .anyMatch(r -> r.getStatus() == Status.MIGRATING || r.getStatus() == Status.PROCESSING);
            if (hasInFlightRequest) {
                log.warn("[{}] userId={} 승인/마이그레이션 진행 중인 요청이 있어 정리를 거부합니다.", logPrefix, userId);
                throw new ConflictException(ErrorCode.REQUEST_MIGRATION_IN_PROGRESS);
            }
            if (managed.beginUbuntuAccountRelease()) {
                // 이전 회수 주기나 실패한 시도의 계정 회수 작업 번호를 비워 모든 노드를 새로 등록하게 한다.
                current.stream()
                        .filter(r -> r.getStatus() == Status.DELETED && r.getJobId() != null)
                        .forEach(Request::forgetAccountRevokeJob);
            }
            return current;
        });

        List<Long> failedRequestIds = new ArrayList<>();
        for (Request request : targets) {
            Long requestId = request.getRequestId();
            switch (request.getStatus()) {
                case FULFILLED -> {
                    try {
                        requestExpiryService.startContainerRevoke(requestId);
                    } catch (Exception e) {
                        log.error("[{}] userId={} requestId={} 컨테이너 회수 등록 실패 — 다음 요청을 계속 정리합니다: {}",
                                logPrefix, userId, requestId, e.getMessage());
                        failedRequestIds.add(requestId);
                    }
                }
                case PENDING, DENIED -> newTx.execute(status -> {
                    requestRepository.findByIdForUpdate(requestId)
                            .filter(r -> r.getStatus() == Status.PENDING || r.getStatus() == Status.DENIED)
                            .ifPresent(Request::delete);
                    return null;
                });
                default -> {
                    // EXPIRING: 이미 회수 중. DELETED: 끝남.
                }
            }
        }

        if (!failedRequestIds.isEmpty()) {
            // 컨테이너가 남은 동안 계정 회수 폴러는 기다린다. 관리자가 다시 누르면 남은 컨테이너부터 다시 회수한다.
            try {
                alarmService.sendSlackAlert(String.format(
                        "[%s] userId=%d 컨테이너 회수 등록 실패 - 확인 후 다시 실행해 주세요: requestIds=%s",
                        logPrefix, userId, failedRequestIds), null);
            } catch (Exception ignored) {
                // 알림 발송 실패가 원래 예외 전파를 막으면 안 된다.
            }
            throw new BusinessException(ErrorCode.USER_REQUEST_CLEANUP_PARTIALLY_FAILED);
        }
        log.info("[{}] userId={} 정리 시작 완료 — 컨테이너·계정 회수는 결과 폴러가 마무리한다", logPrefix, userId);
    }

    /**
     * 유저 삭제 (soft delete 적용)
     * 웹 계정은 바로 탈퇴 처리하고, 소유한 컨테이너와 우분투 계정의 회수를 시작한다(결과 폴러가 마무리).
     */
    // cleanupUserRequests가 요청마다 작업 등록 HTTP를 호출하므로, 그 동안 트랜잭션과
    // DB 커넥션을 붙잡지 않도록 바깥 트랜잭션을 열지 않는다. 상태를 바꾸는 구간만 아래에서
    // 짧은 트랜잭션으로 따로 연다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteUser(Long userId) {
        log.warn("[deleteUser] userId={} 논리적 삭제 시도", userId);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        User user = tx.execute(status -> userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("[deleteUser] userId={} 존재하지 않음", userId);
                    return new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND);
                }));

        cleanupUserRequests(user, "deleteUser");

        User withdrawn = tx.execute(status -> {
            User managed = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
            managed.withdraw();
            return managed;
        });
        // 남아있는 리프레시 토큰으로 액세스 토큰을 계속 재발급받지 못하도록 함께 폐기한다.
        tokenService.logout(userId);
        log.info("[deleteUser] userId={} 논리적 삭제 완료 (isActive=false)", userId);

        try {
            String subject = messageUtils.get("notification.user.admin-delete.subject");
            String body = messageUtils.get("notification.user.admin-delete.body", withdrawn.getName());
            alarmService.sendAllAlerts(withdrawn.getName(), withdrawn.getEmail(), subject, body);
        } catch (Exception e) {
            log.warn("[deleteUser] 계정 비활성화 안내 메일 발송 실패: userId={}", userId, e);
        }
    }

    /**
     * 사용자 번호로 그 사용자의 우분투 계정 회수를 시작한다. 살아 있는 신청의 컨테이너를 모두 회수한 뒤 계정을
     * 지운다(결과 폴러가 마무리). 웹 계정은 그대로 남고, 다시 신청해 승인되면 같은 UID·홈으로 계정이 되살아난다.
     * 회수 중인 계정에 다시 부르면 실패한 단계를 다시 등록한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteUbuntuAccountOfUser(Long userId) {
        User user = new TransactionTemplate(transactionManager).execute(status -> userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND)));
        if (user.getUbuntuAccountStatus() == UbuntuAccountStatus.NONE) {
            throw new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND);
        }
        log.warn("[deleteUbuntuAccount] 우분투 계정 회수 시작: userId={}", userId);
        cleanupUserRequests(user, "deleteUbuntuAccount");
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
    // deleteUser와 같은 이유로 바깥 트랜잭션을 열지 않는다 (cleanupUserRequests의 HTTP 호출).
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserSummaryDTO deactivateUser(Long userId) {
        log.info("[deactivateUser] userId={} 비활성화 시도", userId);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        User user = tx.execute(status -> {
            User found = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
            if (!found.getIsActive()) {
                log.warn("[deactivateUser] userId={} 이미 비활성화 상태", userId);
                throw new ConflictException(ErrorCode.USER_ALREADY_INACTIVE);
            }
            return found;
        });

        cleanupUserRequests(user, "deactivateUser");

        // 응답 DTO는 반드시 이 트랜잭션이 반환한 인스턴스로 만들어야 한다 — 위에서 조회한
        // user는 별개의 짧은 트랜잭션에서 읽어온 뒤 준영속 상태라, 그걸로 DTO를 만들면
        // 방금 비활성화했는데도 isActive=true인 응답이 나간다.
        User deactivated = tx.execute(status -> {
            User managed = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
            managed.deactivate();
            return managed;
        });
        tokenService.logout(userId);
        log.info("[deactivateUser] userId={} 비활성화 완료 (컨테이너 정리 포함)", userId);

        try {
            String subject = messageUtils.get("notification.user.admin-delete.subject");
            String body = messageUtils.get("notification.user.admin-delete.body", deactivated.getName());
            alarmService.sendAllAlerts(deactivated.getName(), deactivated.getEmail(), subject, body);
        } catch (Exception e) {
            log.warn("[deactivateUser] 계정 비활성화 안내 메일 발송 실패: userId={}", userId, e);
        }

        return UserSummaryDTO.fromEntity(deactivated);
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
