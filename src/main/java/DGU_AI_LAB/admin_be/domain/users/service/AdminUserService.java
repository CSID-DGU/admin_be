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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
     * 유저가 소유한 모든 Request(컨테이너)를 정리하고, 마지막에 우분투 계정 자체를 회수한다.
     * FULFILLED 상태는 외부 Pod를 삭제하고, 나머지는 논리 삭제한다.
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
        // 마지막에 우분투 계정을 지울 때 config-server에 넘길 farm 노드 목록. 살아있던
        // 컨테이너가 있었다면 그 노드들로 삭제 범위를 좁힌다 — 안 넘기면 config-server가
        // 모든 farm 노드를 훑어서 같은 유저네임의 무관한 레거시 계정까지 지울 수 있다.
        // 한 사용자가 신청을 여러 개 동시에 가질 수 있어 서로 다른 노드에 컨테이너가 떠
        // 있을 수 있으므로, 마지막 신청의 노드 하나만 남기면 다른 노드의 계정이 안 지워진
        // 채로 남는다 — Set으로 정리한 노드를 전부 모아 releaseUbuntuAccount에서 각각 지운다.
        Set<String> cleanedNodeNames = new LinkedHashSet<>();
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

                // 우분투 계정은 여기서 지우지 않는다 — 사용자가 컨테이너를 여러 번 받아도
                // 계정은 하나뿐이라, 요청마다 지우면 두 번째 요청 정리에서 이미 없는 계정을
                // 다시 지우게 된다. 계정 회수는 모든 요청을 정리한 뒤 아래에서 한 번만 한다.
                try {
                    podService.deletePod(podNameRef[0]);
                } catch (Exception e) {
                    log.error("[{}] userId={} requestId={} Pod 삭제 실패 — 이 요청은 FULFILLED로 남기고 다음 요청을 계속 정리합니다: {}",
                            logPrefix, user.getUserId(), requestId, e.getMessage());
                    failedRequestIds.add(requestId);
                    revertToFulfilled(requestId);
                    try {
                        alarmService.sendSlackAlert(String.format(
                                "[%s] userId=%d 정리 중 Pod 삭제 실패 - 수동 확인 필요: requestId=%d, ubuntuUsername=%s",
                                logPrefix, user.getUserId(), requestId, usernameRef[0]), null);
                    } catch (Exception ignored) {
                        // 알림 발송 실패가 다른 요청 정리를 막으면 안 된다.
                    }
                    continue;
                }
                // 정상적인 FULFILLED 요청이라면 항상 채워져 있어야 하지만, 방어적으로 null이면
                // 건너뛴다 — Set에 null이 섞이면 releaseUbuntuAccount가 node_name=null로 계정
                // 삭제를 호출해, 모든 farm 노드를 훑는 "hold-and-alert" 안전장치를 우회하게 된다.
                if (nodeNameRef[0] != null) {
                    cleanedNodeNames.add(nodeNameRef[0]);
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
            // Pod가 남아있는 요청이 있으면 계정을 지우지 않는다 — 계정 없이 떠 있는 Pod가 된다.
            throw new BusinessException(ErrorCode.USER_REQUEST_CLEANUP_PARTIALLY_FAILED);
        }
        releaseUbuntuAccount(user.getUserId(), cleanedNodeNames, logPrefix);
        log.info("[{}] userId={}와 연결된 Request 정리 완료", logPrefix, user.getUserId());
    }

    /**
     * 사용자가 삭제/비활성화될 때만 실행되는 우분투 계정 회수.
     * 유저네임은 웹 계정에 평생 귀속되므로 남기고, config-server의 리눅스 계정과 UID/GID만 지운다 —
     * UID는 삭제 즉시 다른 사용자에게 재할당될 수 있어, 들고 있으면 이 계정이 되살아났을 때
     * 남의 UID로 Pod를 만든다.
     *
     * @param cleanedNodeNames 방금 정리한 컨테이너들이 떠 있던 노드 전부. 한 사용자가 신청을
     *                         여러 개 동시에 가질 수 있어 서로 다른 노드에 컨테이너가 있을 수
     *                         있으므로, 노드마다 개별적으로 계정 삭제를 호출해야 한다.
     *                         컨테이너가 이미 만료된 사용자는 비어있으므로 그때는 신청 이력에서
     *                         노드를 되찾는다.
     * 일부 노드에서만 계정 삭제가 실패하면 UID/GID는 회수하지 않고
     * {@link ErrorCode#USER_REQUEST_CLEANUP_PARTIALLY_FAILED}를 던진다 — 이미 지워진
     * 노드와 실패한 노드가 섞인 채로 회수해버리면, 남은 노드의 계정이 DB엔 없는 걸로
     * 기록된 채 실제로는 살아남는다.
     */
    private void releaseUbuntuAccount(Long userId, Set<String> cleanedNodeNames, String logPrefix) {
        TransactionTemplate newTx = new TransactionTemplate(transactionManager);
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        final String[] usernameRef = {null};
        final Set<String> nodeNamesRef = new LinkedHashSet<>(cleanedNodeNames);
        newTx.execute(status -> {
            User managed = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
            if (!managed.hasUbuntuAccount()) {
                return null;
            }
            usernameRef[0] = managed.getUbuntuUsername();
            if (nodeNamesRef.isEmpty()) {
                requestRepository.findNodeNamesByUserIdOrderByRequestIdDesc(userId)
                        .stream().findFirst().ifPresent(nodeNamesRef::add);
            }
            return null;
        });
        if (usernameRef[0] == null) {
            log.info("[{}] userId={} 배정된 우분투 계정이 없어 계정 삭제를 건너뜁니다.", logPrefix, userId);
            return;
        }
        if (nodeNamesRef.isEmpty()) {
            // node_name 없이 삭제를 호출하면 config-server가 모든 farm 노드를 훑어서 같은
            // 유저네임의 무관한 레거시 계정까지 지운다. 그 위험을 감수하느니 계정을 남기고
            // 관리자에게 알린다 — 남은 계정은 수동으로 정리할 수 있지만, 잘못 지운 남의
            // 계정은 되돌릴 수 없다. UID/GID도 함께 남겨 이 사용자의 상태를 그대로 보존한다.
            log.error("[{}] userId={} 계정이 배포된 farm 노드를 알 수 없어 계정 삭제를 보류합니다 - 수동 정리 필요: username={}",
                    logPrefix, userId, usernameRef[0]);
            try {
                alarmService.sendSlackAlert(String.format(
                        "[%s] userId=%d 우분투 계정의 farm 노드를 알 수 없어 삭제 보류 - 수동 정리 필요: ubuntuUsername=%s",
                        logPrefix, userId, usernameRef[0]), null);
            } catch (Exception ignored) {
                // 알림 발송 실패가 사용자 삭제 자체를 막으면 안 된다.
            }
            return;
        }

        List<String> failedNodeNames = new ArrayList<>();
        for (String nodeName : nodeNamesRef) {
            try {
                ubuntuAccountService.deleteUbuntuAccount(usernameRef[0], nodeName);
            } catch (Exception e) {
                log.error("[{}] userId={} 우분투 계정 삭제 실패 - 수동 확인 필요: username={}, node={}",
                        logPrefix, userId, usernameRef[0], nodeName, e);
                failedNodeNames.add(nodeName);
            }
        }
        if (!failedNodeNames.isEmpty()) {
            // 일부 노드에서만 계정을 지운 채 UID/GID를 회수하면, 실패한 노드엔 계정이 남아있는데
            // DB는 이미 회수됐다고 기록해 다음 승인이 새 UID를 발급하고 그 노드에서 충돌한다.
            // 하나라도 실패하면 UID/GID를 그대로 두고, 이미 지워진 노드까지 포함해 수동 확인을
            // 요청한다 — 이미 지워진 노드를 다시 지우려 하면 config-server가 404로 응답할 뿐이라
            // 재시도 자체는 안전하다.
            try {
                alarmService.sendSlackAlert(String.format(
                        "[%s] userId=%d 우분투 계정 삭제 일부 실패 - 수동 확인 필요: ubuntuUsername=%s, 실패한 노드=%s",
                        logPrefix, userId, usernameRef[0], failedNodeNames), null);
            } catch (Exception ignored) {
                // 알림 발송 실패가 원래 예외 전파를 막으면 안 된다.
            }
            throw new BusinessException(ErrorCode.USER_REQUEST_CLEANUP_PARTIALLY_FAILED);
        }
        newTx.execute(status -> {
            userRepository.findByIdForUpdate(userId).ifPresent(User::releaseUbuntuAccount);
            return null;
        });
        log.info("[{}] userId={} 우분투 계정 삭제 완료: username={}, nodes={}", logPrefix, userId, usernameRef[0], nodeNamesRef);
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
     * 이 웹 계정에 딸린 살아있는 신청을 전부 순회하며 Pod/계정을 정리한다 — deleteUser/
     * deactivateUser와 같은 cleanupUserRequests를 공유한다. 한 사용자가 신청을 여러 개
     * 동시에 가질 수 있게 된 뒤로는 "가장 최근 신청 하나만" 지우고 UID/GID를 통째로
     * 회수하면, 다른 노드에 남은 컨테이너가 DB가 추적하지 않는 UID로 계속 도는 상태가
     * 된다. deleteUser와 달리 웹 계정(User) 자체는 탈퇴/비활성화하지 않는다 — 사용자는
     * 그대로 남아있고 리눅스 계정만 회수되며, 다시 신청하면 새 계정을 받는다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteUbuntuAccount(String username) {
        log.warn("[deleteUbuntuAccount] 우분투 계정 삭제 시도: {}", username);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        User user = tx.execute(status -> userRepository.findByUbuntuUsername(username)
                .filter(User::hasUbuntuAccount)
                .orElseThrow(() -> {
                    log.warn("[deleteUbuntuAccount] {}에 해당하는 계정이 없습니다.", username);
                    return new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND);
                }));

        cleanupUserRequests(user, "deleteUbuntuAccount");
        log.info("[deleteUbuntuAccount] {} 계정 삭제 및 DB 상태 업데이트 완료", username);
    }

    /**
     * 컨테이너(신청) 하나만 삭제 — 관리자 콘솔의 개별 컨테이너 상세 페이지 삭제 버튼용.
     * deleteUbuntuAccount(username)는 그 유저네임에 딸린 살아있는 신청을 전부 순회해서
     * 지우므로, 한 사용자가 컨테이너를 여러 개 동시에 가진 상태에서 그 엔드포인트를 개별
     * 컨테이너 삭제에 쓰면 나머지 컨테이너까지 같이 지워진다 — 이 메서드는 요청받은
     * requestId 하나만 정리하고, 같은 사용자의 다른 살아있는 신청이 남아있으면 우분투
     * 계정(UID/GID)은 회수하지 않는다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteSingleContainer(Long requestId) {
        log.warn("[deleteSingleContainer] requestId={} 컨테이너 삭제 시도", requestId);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        final String[] podNameRef = {null};
        final String[] nodeNameRef = {null};
        final String[] usernameRef = {null};
        final Long[] userIdRef = {null};
        tx.execute(status -> {
            Request request = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
            if (request.getStatus() == Status.MIGRATING || request.getStatus() == Status.PROCESSING
                    || request.getStatus() == Status.EXPIRING) {
                log.warn("[deleteSingleContainer] requestId={} 승인/마이그레이션 진행 중이라 삭제를 거부합니다.", requestId);
                throw new ConflictException(ErrorCode.REQUEST_MIGRATION_IN_PROGRESS);
            }
            if (request.getStatus() != Status.FULFILLED) {
                log.warn("[deleteSingleContainer] requestId={} FULFILLED 상태가 아니라 삭제를 거부합니다. 현재 상태={}",
                        requestId, request.getStatus());
                throw new ConflictException(ErrorCode.INVALID_REQUEST_STATUS);
            }
            request.beginExpiry();
            podNameRef[0] = request.getPodName();
            nodeNameRef[0] = request.getNodeName();
            usernameRef[0] = request.getUbuntuUsername();
            userIdRef[0] = request.getUser().getUserId();
            return null;
        });

        try {
            podService.deletePod(podNameRef[0]);
        } catch (Exception e) {
            log.error("[deleteSingleContainer] requestId={} Pod 삭제 실패: {}", requestId, e.getMessage());
            revertToFulfilled(requestId);
            try {
                alarmService.sendSlackAlert(String.format(
                        "[deleteSingleContainer] requestId=%d Pod 삭제 실패 - 수동 확인 필요: ubuntuUsername=%s",
                        requestId, usernameRef[0]), null);
            } catch (Exception ignored) {
                // 알림 발송 실패가 원래 예외 전파를 막으면 안 된다.
            }
            throw new BusinessException(ErrorCode.USER_REQUEST_CLEANUP_PARTIALLY_FAILED);
        }

        final Request[] deletedRef = {null};
        tx.execute(status -> {
            Request managed = requestRepository.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ENTITY_NOT_FOUND));
            managed.deleteAfterCleanup();
            managed.getUser().getEmail();
            managed.getResourceGroup().getServerName();
            deletedRef[0] = managed;
            return null;
        });
        try {
            alarmService.sendContainerDeletedEmail(deletedRef[0]);
        } catch (Exception e) {
            log.warn("[deleteSingleContainer] 삭제 안내 메일 발송 실패: ubuntuUsername={}", usernameRef[0], e);
        }

        // deleteAfterCleanup()으로 이 요청은 이미 DELETED라 openStatuses()에 안 잡힌다 —
        // 이 조회에 남는 게 있다면 전부 "다른" 살아있는 신청이다. 남아있으면 그 컨테이너들이
        // 이 UID/GID를 계속 쓰고 있으므로 계정은 회수하지 않는다.
        List<Request> otherLiveRequests = tx.execute(status ->
                requestRepository.findAllByUser_UserIdAndStatusIn(userIdRef[0], Status.openStatuses()));
        if (otherLiveRequests != null && !otherLiveRequests.isEmpty()) {
            log.info("[deleteSingleContainer] requestId={} 삭제 완료 — userId={}의 다른 살아있는 신청이 {}건 남아있어 계정은 유지합니다.",
                    requestId, userIdRef[0], otherLiveRequests.size());
            return;
        }

        Set<String> nodeNames = nodeNameRef[0] != null ? Set.of(nodeNameRef[0]) : Set.of();
        releaseUbuntuAccount(userIdRef[0], nodeNames, "deleteSingleContainer");
        log.info("[deleteSingleContainer] requestId={} 컨테이너 및 계정 삭제 완료", requestId);
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
