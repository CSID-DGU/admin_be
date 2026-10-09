package DGU_AI_LAB.admin_be.domain.warnings.entity;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계정의 접속 차단·해제 작업 하나. 접속은 config-server 작업으로만 막고 풀며, 한 계정의 모든 컨테이너·모든 포트가
 * 한 작업의 대상이다. 마지막으로 성공한 작업의 {@code blocked}가 지금 실제로 적용된 상태다.
 * 상태 전이는 {@link AccessOperationStatus} 참고.
 */
@Entity
@Getter
@Table(name = "access_operations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessOperation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "access_operation_id")
    private Long accessOperationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccessOperationStatus status = AccessOperationStatus.PROCESSING;

    /** true면 차단, false면 해제. */
    @Column(name = "blocked", nullable = false)
    private boolean blocked;

    /** 작업을 등록할 때의 리눅스 계정. */
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    /** 등록한 작업의 번호. 같은 번호로 조회되는 다른 작업의 결과와 가리는 데 쓴다. */
    @Column(name = "job_id")
    private Long jobId;

    /** 실패한 작업의 오류 코드(config-server 의 error_code). */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public AccessOperation(User user, boolean blocked) {
        this.user = user;
        this.username = user.getUbuntuUsername();
        this.blocked = blocked;
    }

    public void registered(Long jobId) {
        this.jobId = jobId;
    }

    public void markApplied() {
        requireProcessing();
        this.status = AccessOperationStatus.APPLIED;
    }

    public void markFailed(String errorCode) {
        requireProcessing();
        this.status = AccessOperationStatus.FAILED;
        this.errorCode = errorCode;
    }

    public boolean isProcessing() {
        return status == AccessOperationStatus.PROCESSING;
    }

    private void requireProcessing() {
        if (!isProcessing()) {
            throw new BusinessException("반영 중인 접속 작업이 아닙니다.", ErrorCode.INVALID_REQUEST_STATUS);
        }
    }
}
