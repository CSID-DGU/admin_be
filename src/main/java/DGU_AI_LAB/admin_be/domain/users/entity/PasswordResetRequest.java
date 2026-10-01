package DGU_AI_LAB.admin_be.domain.users.entity;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 비밀번호 재설정 신청. 새 비밀번호를 정하는 것은 사용자(또는 대신 정해 주는 관리자)지만, 컨테이너까지 바꾸는
 * 일은 관리자 승인을 거쳐 config-server 작업으로만 한다. 상태 전이는 {@link PasswordResetStatus} 참고.
 *
 * <p>새 비밀번호는 웹 로그인용(BCrypt)·리눅스 계정용(SHA-512 crypt) 해시로만 들고, 적용되거나 거절되면 비운다.
 */
@Entity
@Getter
@Table(name = "password_reset_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "password_reset_request_id")
    private Long passwordResetRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PasswordResetStatus status = PasswordResetStatus.PENDING;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "ubuntu_password_hash")
    private String ubuntuPasswordHash;

    /** 컨테이너에 반영하는 작업의 번호. 같은 신청을 다시 승인하면 새 작업이라, 어느 작업의 결과인지 이 번호로 가린다. */
    @Column(name = "job_id")
    private Long jobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    private PasswordResetRequest(User user, PasswordHashes hashes) {
        this.user = user;
        this.passwordHash = hashes.web();
        this.ubuntuPasswordHash = hashes.ubuntu();
    }

    public static PasswordResetRequest pending(User user, PasswordHashes hashes) {
        return new PasswordResetRequest(user, hashes);
    }

    /** 승인 전이면 새 비밀번호를 다시 정할 수 있다. 사용자가 신청을 다시 낸 경우다. */
    public void replacePassword(PasswordHashes hashes) {
        ensurePending();
        this.passwordHash = hashes.web();
        this.ubuntuPasswordHash = hashes.ubuntu();
    }

    public void startProcessing(User admin, Long jobId) {
        ensurePending();
        review(admin);
        this.jobId = jobId;
        this.status = PasswordResetStatus.PROCESSING;
    }

    /** 리눅스 계정이 없어 컨테이너에 반영할 것이 없을 때, 승인과 함께 바로 적용한다. */
    public void applyWithoutJob(User admin) {
        ensurePending();
        review(admin);
        markApplied();
    }

    public void completeJob() {
        requireProcessing();
        markApplied();
    }

    /** 작업이 실패했다. 다시 승인하거나 거절할 수 있게 승인 전으로 되돌린다. */
    public void returnToPending() {
        requireProcessing();
        this.status = PasswordResetStatus.PENDING;
        this.jobId = null;
        this.reviewedBy = null;
        this.reviewedAt = null;
    }

    public void deny(User admin) {
        ensurePending();
        review(admin);
        this.status = PasswordResetStatus.DENIED;
        clearPassword();
    }

    /** 신청자가 비활성화·탈퇴돼 검토 없이 닫는다. 검토자가 없는 DENIED로 남는다. */
    public void closeWithoutReview() {
        ensurePending();
        this.status = PasswordResetStatus.DENIED;
        clearPassword();
    }

    public PasswordHashes hashes() {
        return new PasswordHashes(passwordHash, ubuntuPasswordHash);
    }

    private void markApplied() {
        this.status = PasswordResetStatus.APPLIED;
        clearPassword();
    }

    private void review(User admin) {
        this.reviewedBy = admin;
        this.reviewedAt = LocalDateTime.now();
    }

    private void clearPassword() {
        this.passwordHash = null;
        this.ubuntuPasswordHash = null;
    }

    /** 승인 전인지 확인한다. 반영 중이면 PASSWORD_RESET_IN_PROGRESS, 끝난 신청이면 PASSWORD_RESET_ALREADY_CLOSED. */
    public void ensurePending() {
        if (status == PasswordResetStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_IN_PROGRESS);
        }
        if (status != PasswordResetStatus.PENDING) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_ALREADY_CLOSED);
        }
    }

    private void requireProcessing() {
        if (status != PasswordResetStatus.PROCESSING) {
            throw new BusinessException("적용 중인 재설정 신청이 아닙니다.", ErrorCode.INVALID_REQUEST_STATUS);
        }
    }
}
