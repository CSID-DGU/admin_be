package DGU_AI_LAB.admin_be.domain.users.entity;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 비밀번호 변경 요청(변경 요청 종류 PASSWORD)을 컨테이너에 반영하는 데 필요한 값. 새 비밀번호를 정하는 것은
 * 사용자(또는 대신 정해 주는 관리자)지만, 컨테이너까지 바꾸는 일은 관리자 승인을 거쳐 config-server 작업으로만 한다.
 *
 * <p>상태·검토자·검토 시각은 변경 요청({@link ChangeRequest})에만 있다. 여기서는 그 상태를 바꾸면서 함께 움직여야
 * 하는 값(해시, 작업 번호)을 같이 다룬다.
 *
 * <pre>
 * PENDING ──승인──▶ PROCESSING ──작업 성공──▶ FULFILLED
 *    │  ▲               │
 *    │  └──작업 실패─────┘
 *    ├──승인(리눅스 계정 없음)──▶ FULFILLED
 *    └──거절──▶ DENIED
 * </pre>
 *
 * <p>새 비밀번호는 웹 로그인용(BCrypt)·리눅스 계정용(SHA-512 crypt) 해시로만 들고, 적용되거나 거절되면 비운다.
 */
@Entity
@Getter
@Table(name = "password_reset_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetRequest extends BaseTimeEntity {

    /** config-server 작업 기록의 키로 쓴다(password-reset-번호). 관리자·사용자에게 보이는 번호는 변경 요청 번호다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "password_reset_request_id")
    private Long passwordResetRequestId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_request_id", nullable = false, unique = true)
    private ChangeRequest changeRequest;

    /** 비밀번호가 바뀌는 계정. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "ubuntu_password_hash")
    private String ubuntuPasswordHash;

    /** 컨테이너에 반영하는 작업의 번호. 같은 신청을 다시 승인하면 새 작업이라, 어느 작업의 결과인지 이 번호로 가린다. */
    @Column(name = "job_id")
    private Long jobId;

    private PasswordResetRequest(ChangeRequest changeRequest, User user, PasswordHashes hashes) {
        this.changeRequest = changeRequest;
        this.user = user;
        this.passwordHash = hashes.web();
        this.ubuntuPasswordHash = hashes.ubuntu();
    }

    public static PasswordResetRequest pending(ChangeRequest changeRequest, User user, PasswordHashes hashes) {
        return new PasswordResetRequest(changeRequest, user, hashes);
    }

    public Status getStatus() {
        return changeRequest.getStatus();
    }

    /** 승인 전이면 새 비밀번호를 다시 정할 수 있다. 사용자가 신청을 다시 낸 경우다. */
    public void replacePassword(PasswordHashes hashes) {
        ensurePending();
        this.passwordHash = hashes.web();
        this.ubuntuPasswordHash = hashes.ubuntu();
    }

    public void startProcessing(User admin, String adminComment, Long jobId) {
        ensurePending();
        changeRequest.startProcessing(admin, adminComment);
        this.jobId = jobId;
    }

    /** 리눅스 계정이 없어 컨테이너에 반영할 것이 없을 때, 승인과 함께 바로 적용한다. */
    public void applyWithoutJob(User admin, String adminComment) {
        ensurePending();
        changeRequest.approve(admin, adminComment);
        clearPassword();
    }

    public void completeJob() {
        requireProcessing();
        changeRequest.completeProcessing();
        clearPassword();
    }

    /** 작업이 실패했다. 다시 승인하거나 거절할 수 있게 승인 전으로 되돌린다. */
    public void returnToPending() {
        requireProcessing();
        changeRequest.returnToPending();
        this.jobId = null;
    }

    public void deny(User admin, String adminComment) {
        ensurePending();
        changeRequest.deny(admin, adminComment);
        clearPassword();
    }

    /** 신청자가 비활성화·탈퇴돼 검토 없이 닫는다. */
    public void closeWithoutReview() {
        ensurePending();
        changeRequest.closeWithoutReview();
        clearPassword();
    }

    public PasswordHashes hashes() {
        return new PasswordHashes(passwordHash, ubuntuPasswordHash);
    }

    private void clearPassword() {
        this.passwordHash = null;
        this.ubuntuPasswordHash = null;
    }

    /** 승인 전인지 확인한다. 반영 중이면 PASSWORD_RESET_IN_PROGRESS, 끝난 신청이면 PASSWORD_RESET_ALREADY_CLOSED. */
    public void ensurePending() {
        if (getStatus() == Status.PROCESSING) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_IN_PROGRESS);
        }
        if (getStatus() != Status.PENDING) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_ALREADY_CLOSED);
        }
    }

    private void requireProcessing() {
        if (getStatus() != Status.PROCESSING) {
            throw new BusinessException("적용 중인 재설정 신청이 아닙니다.", ErrorCode.INVALID_REQUEST_STATUS);
        }
    }
}
