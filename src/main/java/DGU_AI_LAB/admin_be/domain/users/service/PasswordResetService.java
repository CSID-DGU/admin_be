package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.PasswordChangeRegisterRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.job.JobClient;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.dto.response.PasswordResetSummaryDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordHashes;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetRequest;
import DGU_AI_LAB.admin_be.domain.users.entity.PasswordResetStatus;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.PasswordResetRequestRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 비밀번호 재설정 신청의 상태를 바꾼다: 신청 → 관리자 승인 → config-server 작업 → 결과 반영
 * (상태는 {@link PasswordResetStatus}).
 *
 * <p>웹 비밀번호가 곧 SSH(Ubuntu) 비밀번호라, 컨테이너에 반영된 것을 확인한 뒤에야 DB의 두 해시를 바꾼다.
 * 반영은 작업으로 등록만 하고 돌아오고, 결과는 PasswordResetJobPoller가 {@link #complete}·
 * {@link #returnToPending}으로 반영한다.
 *
 * <p>사용자 행 잠금으로 트랜잭션을 시작한다. 컨테이너 승인도 같은 행을 잠그고 SSH 해시를 읽으므로, 한 사용자에게
 * 비밀번호 교체 작업과 컨테이너 생성 작업이 함께 돌지 않는다 — 겹치면 새 컨테이너만 옛 비밀번호로 만들어진다.
 * 잠금이 트랜잭션의 첫 읽기여야 그 뒤의 확인이 잠금을 얻은 시점의 상태를 본다(먼저 읽으면 그때의 스냅샷으로 본다).
 *
 * <p>신청 행만 바꾸는 거절·되돌리기도 사용자 행부터 잠근다. 신청 행을 고치면 DB가 외래 키 확인으로 사용자 행을
 * 잠그므로, 신청 행부터 잠그면 승인(사용자 → 신청)과 잠금 순서가 반대가 돼 동시에 눌렀을 때 교착으로 500이 난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final List<PasswordResetStatus> OPEN =
            List.of(PasswordResetStatus.PENDING, PasswordResetStatus.PROCESSING);

    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final PasswordResetRequestRepository resetRepository;
    private final JobClient jobClient;
    private final TokenService tokenService;
    private final UserLoginService userLoginService;
    private final PasswordResetNotifier notifier;
    private final PlatformTransactionManager transactionManager;

    /** @param created false면 승인을 기다리던 신청의 새 비밀번호만 바꿨다 */
    public record Submission(PasswordResetSummaryDTO request, boolean created) {}

    private record Approval(PasswordResetSummaryDTO request, boolean applied) {}

    /**
     * 재설정 신청을 낸다. 아무것도 바꾸지 않고 승인 대기로 둔다. 사용자당 열린 신청은 하나라, 승인 전에 다시 내면
     * 그 신청의 새 비밀번호만 바뀐다.
     *
     * @throws BusinessException 앞선 신청을 컨테이너에 반영하는 중이면(409, PASSWORD_RESET_IN_PROGRESS)
     */
    public Submission submit(Long userId, PasswordHashes hashes) {
        return inTransaction(() -> {
            User user = lockUser(userId);
            Optional<PasswordResetRequest> open =
                    resetRepository.findAllByUser_UserIdAndStatusIn(userId, OPEN).stream().findFirst();
            if (open.isPresent()) {
                open.get().replacePassword(hashes);
                return new Submission(PasswordResetSummaryDTO.fromEntity(open.get()), false);
            }
            PasswordResetRequest reset = resetRepository.save(PasswordResetRequest.pending(user, hashes));
            log.info("[passwordReset] resetId={} userId={} 재설정 신청 접수", reset.getPasswordResetRequestId(), userId);
            return new Submission(PasswordResetSummaryDTO.fromEntity(reset), true);
        });
    }

    /**
     * 신청을 승인한다. 리눅스 계정이 있으면 컨테이너에 반영하는 작업을 등록하고 PROCESSING으로 둔 채 돌아온다.
     * 계정이 없으면(첫 승인 전, 또는 회수 뒤) 바꿀 컨테이너가 없으므로 바로 적용한다 — 다음 승인이 이 해시로 계정을 만든다.
     *
     * <p>작업 등록은 트랜잭션 안에서 한다. PROCESSING이 보이는 시점에는 그 작업이 이미 등록돼 있어, 폴러가 같은 신청의
     * 이전 작업 결과를 이번 것으로 읽지 않는다. 등록이 실패하면 신청은 승인 대기로 남는다.
     */
    public PasswordResetSummaryDTO approve(Long resetId, Long adminId) {
        Long userId = userIdOf(resetId);
        Approval approval = inTransaction(() -> {
            User user = lockUser(userId);
            PasswordResetRequest reset = lockReset(resetId);
            reset.ensurePending();
            // 생성 작업은 승인 때 읽은 해시로 계정을 만든다. 그 사이 바꾸면 DB만 새 해시가 되고 새 컨테이너는
            // 옛 비밀번호로 남는다. 승인도 이 사용자 행을 잠그고 해시를 읽으므로 이 확인과 겹치지 않는다.
            if (requestRepository.existsByUser_UserIdAndStatus(userId, Status.PROCESSING)) {
                throw new BusinessException(ErrorCode.UBUNTU_PASSWORD_CHANGE_WHILE_PROVISIONING);
            }
            User admin = userRepository.getReferenceById(adminId);
            if (!user.hasUbuntuAccount()) {
                apply(user, reset.hashes());
                reset.applyWithoutJob(admin);
                return new Approval(PasswordResetSummaryDTO.fromEntity(reset), true);
            }
            Long jobId = jobClient.registerPasswordChange(new PasswordChangeRegisterRequestDTO(
                    resetId, user.getUbuntuUsername(), reset.getUbuntuPasswordHash()));
            reset.startProcessing(admin, jobId);
            return new Approval(PasswordResetSummaryDTO.fromEntity(reset), false);
        });
        log.info("[passwordReset] resetId={} 승인: adminId={}, status={}", resetId, adminId, approval.request().status());
        if (approval.applied()) {
            afterApplied(approval.request().email());
        }
        return approval.request();
    }

    public PasswordResetSummaryDTO deny(Long resetId, Long adminId) {
        Long userId = userIdOf(resetId);
        PasswordResetSummaryDTO denied = inTransaction(() -> {
            lockUser(userId);
            PasswordResetRequest reset = lockReset(resetId);
            reset.deny(userRepository.getReferenceById(adminId));
            return PasswordResetSummaryDTO.fromEntity(reset);
        });
        log.info("[passwordReset] resetId={} 거절: adminId={}", resetId, adminId);
        notifier.denied(denied.email());
        return denied;
    }

    /** 컨테이너 반영 작업이 성공했다. 두 해시를 바꾸고 기존 로그인을 끊는다. 이미 반영된 신청이면 아무것도 하지 않는다. */
    public void complete(Long resetId) {
        Long userId = userIdOf(resetId);
        String email = inTransaction(() -> {
            User user = lockUser(userId);
            PasswordResetRequest reset = lockReset(resetId);
            if (reset.getStatus() != PasswordResetStatus.PROCESSING) {
                return null;
            }
            apply(user, reset.hashes());
            reset.completeJob();
            return user.getEmail();
        });
        if (email == null) {
            return;
        }
        log.info("[passwordReset] resetId={} 적용 완료(SSH 포함)", resetId);
        afterApplied(email);
    }

    /**
     * 컨테이너 반영 작업이 성공하지 못했다. 다시 승인하거나 거절할 수 있게 승인 대기로 되돌린다.
     *
     * @return 되돌린 신청. 이미 다른 상태면 빈 값
     */
    public Optional<PasswordResetSummaryDTO> returnToPending(Long resetId) {
        Long userId = userIdOf(resetId);
        return inTransaction(() -> {
            lockUser(userId);
            PasswordResetRequest reset = lockReset(resetId);
            if (reset.getStatus() != PasswordResetStatus.PROCESSING) {
                return Optional.empty();
            }
            reset.returnToPending();
            return Optional.of(PasswordResetSummaryDTO.fromEntity(reset));
        });
    }

    /** 관리자가 처리할 신청(승인 대기·반영 중)을 최근 순으로 돌려준다. */
    public List<PasswordResetSummaryDTO> getOpenRequests() {
        return resetRepository.findAllWithUserByStatusIn(OPEN).stream()
                .map(PasswordResetSummaryDTO::fromEntity)
                .toList();
    }

    private void apply(User user, PasswordHashes hashes) {
        user.updatePassword(hashes.web());
        user.changeUbuntuPasswordHash(hashes.ubuntu());
        // 새거나 잊은 비밀번호로 이미 들어와 있는 세션을 끊는다(리프레시 토큰 삭제).
        tokenService.logout(user.getUserId());
    }

    /** 비밀번호는 이미 바뀌었으므로 뒤처리가 실패해도 적용을 실패로 돌리지 않는다. */
    private void afterApplied(String email) {
        try {
            // 잊은 비밀번호로 실패를 쌓아 잠긴 사용자가 새 비밀번호로 바로 들어올 수 있게 한다.
            userLoginService.clearFailedAttempts(email);
        } catch (RuntimeException e) {
            log.warn("[passwordReset] 로그인 실패 횟수 삭제 실패", e);
        }
        notifier.applied(email);
    }

    private Long userIdOf(Long resetId) {
        return resetRepository.findUserIdById(resetId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.PASSWORD_RESET_REQUEST_NOT_FOUND));
    }

    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    private PasswordResetRequest lockReset(Long resetId) {
        return resetRepository.findByIdForUpdate(resetId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.PASSWORD_RESET_REQUEST_NOT_FOUND));
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
