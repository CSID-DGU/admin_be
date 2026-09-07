package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
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
    private final UbuntuAccountService ubuntuAccountService;
    private final PodService podService;
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
        TransactionTemplate newTx = new TransactionTemplate(transactionManager);
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 호출자(deleteUser/deactivateUser)는 아래 HTTP 호출 동안 커넥션을 붙잡지 않으려고
        // 트랜잭션을 열지 않는다. 따라서 대상 목록 조회도 여기서 짧은 트랜잭션으로 직접 연다.
        List<Request> userRequests = newTx.execute(status -> requestRepository.findAllByUser(user));

        // MIGRATING뿐 아니라 PROCESSING(승인 처리 중)도 막는다 — 승인 트랜잭션이 진행 중인
        // 사이에 요청이 delete()로 넘어가면, 그 승인이 나중에 완료될 때 이미 소유자가 정리된
        // Request를 FULFILLED로 덮어써 정합성이 깨진다.
        // 이 일괄 사전 검사는 아무것도 건드리기 전에 통째로 거부하기 위한 것이고, 이것만으로는
        // 부족하다 — 요청 하나당 HTTP 2회가 걸려 검사와 실제 삭제 사이에 시간이 벌어지므로,
        // 그 사이 새로 시작된 승인/마이그레이션은 아래 요청별 행 잠금에서 다시 걸러야 한다.
        boolean hasInFlightRequest = userRequests.stream()
                .anyMatch(r -> r.getStatus() == Status.MIGRATING || r.getStatus() == Status.PROCESSING
                        || r.getStatus() == Status.EXPIRING);
        if (hasInFlightRequest) {
            log.warn("[{}] userId={} 승인/마이그레이션 진행 중인 요청이 있어 정리를 거부합니다.", logPrefix, user.getUserId());
            throw new ConflictException(ErrorCode.REQUEST_MIGRATION_IN_PROGRESS);
        }

        List<Long> failedRequestIds = new ArrayList<>();
        for (Request request : userRequests) {
            Long requestId = request.getRequestId();
            if (request.getStatus() == Status.FULFILLED) {
                // 이 요청 하나만 행을 잠그고 FULFILLED -> EXPIRING으로 선점한다. 위 사전 검사
                // 이후 이 요청에 승인/마이그레이션이 새로 시작됐다면 beginExpiry()가 막는다.
                // 삭제 대상 식별자도 위 목록 조회 시점의 준영속 스냅샷이 아니라 여기서 잠그고
                // 다시 읽은 값을 쓴다 — 그 사이 마이그레이션이 끝났다면 podName/nodeName이
                // 바뀌어 있어, 스냅샷대로 지우면 엉뚱한(이미 없는) Pod를 지우고 새 Pod는 남는다.
                final String[] podNameRef = {null};
                final String[] nodeNameRef = {null};
                final String[] usernameRef = {null};
                try {
                    newTx.execute(status -> {
                        Request managed = requestRepository.findByIdForUpdate(requestId)
                                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
                        managed.beginExpiry();
                        podNameRef[0] = managed.getPodName();
                        nodeNameRef[0] = managed.getNodeName();
                        usernameRef[0] = managed.getUbuntuUsername();
                        return null;
                    });
                } catch (Exception e) {
                    log.error("[{}] userId={} requestId={} 정리 직전 상태가 바뀌어 건너뜁니다: {}",
                            logPrefix, user.getUserId(), requestId, e.getMessage());
                    failedRequestIds.add(requestId);
                    continue;
                }

                try {
                    podService.deletePod(podNameRef[0]);
                    ubuntuAccountService.deleteUbuntuAccount(usernameRef[0], nodeNameRef[0]);
                } catch (Exception e) {
                    log.error("[{}] userId={} requestId={} Pod/계정 삭제 실패 — 이 요청은 FULFILLED로 남기고 다음 요청을 계속 정리합니다: {}",
                            logPrefix, user.getUserId(), requestId, e.getMessage());
                    failedRequestIds.add(requestId);
                    revertToFulfilled(requestId);
                    try {
                        alarmService.sendSlackAlert(String.format(
                                "[%s] userId=%d 정리 중 Pod/계정 삭제 실패 - 수동 확인 필요: requestId=%d, ubuntuUsername=%s",
                                logPrefix, user.getUserId(), requestId, usernameRef[0]), null);
                    } catch (Exception ignored) {
                        // 알림 발송 실패가 다른 요청 정리를 막으면 안 된다.
                    }
                    continue;
                }
                final Request[] deletedRef = {null};
                newTx.execute(status -> {
                    Request managed = requestRepository.findByIdForUpdate(requestId)
                            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
                    managed.deleteAfterCleanup();
                    // 트랜잭션 종료 후 메일 발송에서 사용되는 lazy 연관 초기화
                    managed.getUser().getEmail();
                    managed.getResourceGroup().getServerName();
                    deletedRef[0] = managed;
                    return null;
                });
                try {
                    alarmService.sendContainerDeletedEmail(deletedRef[0]);
                } catch (Exception e) {
                    log.warn("[{}] 삭제 안내 메일 발송 실패: ubuntuUsername={}", logPrefix, usernameRef[0], e);
                }
            } else if (request.getStatus() != Status.DELETED) {
                newTx.execute(status -> {
                    Request managed = requestRepository.findByIdForUpdate(requestId)
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
    // cleanupUserRequests가 요청마다 Pod/계정 삭제 HTTP를 호출하므로, 그 동안 트랜잭션과
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
     * 단독 우분투 계정 삭제 (컨트롤러 엔드포인트용)
     * FULFILLED 상태인 Request를 username으로 찾아 외부 계정 삭제 후 DB 상태를 DELETED로 변경한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteUbuntuAccount(String username) {
        log.warn("[deleteUbuntuAccount] 우분투 계정 삭제 시도: {}", username);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. 행 잠금 + 상태 검증 + FULFILLED -> EXPIRING 선점 (짧은 트랜잭션, 이후 커넥션 반납)
        final Long[] requestIdRef = {null};
        final String[] podNameRef = {null};
        final String[] nodeNameRef = {null};
        tx.execute(status -> {
            Long requestId = requestRepository.findByUbuntuUsername(username)
                    .orElseThrow(() -> {
                        log.warn("[deleteUbuntuAccount] {}에 해당하는 Request가 없습니다.", username);
                        return new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND);
                    })
                    .getRequestId();
            Request request = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));

            if (request.getStatus() == Status.DELETED) {
                log.warn("[deleteUbuntuAccount] {}은 이미 DELETED 상태입니다.", username);
                throw new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND);
            }
            // PENDING/PROCESSING/MIGRATING/EXPIRING이면 여기서 막힌다 — 기존에는 DELETED만
            // 걸러서, 승인 처리나 마이그레이션이 진행 중인 요청의 인프라까지 지울 수 있었다.
            request.beginExpiry();
            requestIdRef[0] = requestId;
            podNameRef[0] = request.getPodName();
            nodeNameRef[0] = request.getNodeName();
            return null;
        });
        Long requestId = requestIdRef[0];

        // 2. 외부 HTTP 호출 (DB 커넥션·행 잠금 미보유). 실패 시 EXPIRING에 갇히지 않게 되돌린다.
        try {
            podService.deletePod(podNameRef[0]);
            ubuntuAccountService.deleteUbuntuAccount(username, nodeNameRef[0]);
        } catch (RuntimeException e) {
            revertToFulfilled(requestId);
            throw e;
        }

        // 3. 행 잠금 후 최종 반영 (새 트랜잭션, HTTP 완료 후 짧게만 커넥션 보유)
        final Request[] deletedRef = {null};
        tx.execute(status -> {
            Request request = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
            request.deleteAfterCleanup();
            // 트랜잭션 종료 후 메일 발송에서 사용되는 lazy 연관 초기화
            request.getUser().getEmail();
            request.getResourceGroup().getServerName();
            deletedRef[0] = request;
            return null;
        });
        log.info("[deleteUbuntuAccount] {} 계정 삭제 및 DB 상태 업데이트 완료", username);
        try {
            alarmService.sendContainerDeletedEmail(deletedRef[0]);
        } catch (Exception e) {
            log.warn("[deleteUbuntuAccount] 삭제 안내 메일 발송 실패: ubuntuUsername={}", username, e);
        }
    }

    /**
     * 인프라 삭제에 실패했을 때 EXPIRING에 갇힌 요청을 FULFILLED로 되돌린다.
     * 되돌리지 않으면 그 요청은 재시도도 취소도 못 하는 상태로 남는다. 이 복구 자체의 실패가
     * 원래 예외 전파를 막으면 안 되므로 여기서 삼키고, 정지된 EXPIRING은 재조정 스케줄러가 회수한다.
     */
    private void revertToFulfilled(Long requestId) {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                requestRepository.findByIdForUpdate(requestId)
                        .filter(req -> req.getStatus() == Status.EXPIRING)
                        .ifPresent(Request::endExpiry);
                return null;
            });
        } catch (Exception e) {
            log.error("[AdminUserService] EXPIRING 상태 복구 실패 — 재조정 스케줄러의 회수를 기다린다: requestId={}", requestId, e);
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
