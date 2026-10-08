package DGU_AI_LAB.admin_be.domain.portRequests.entity;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추가 포트 변경 작업 하나. 떠 있는 컨테이너의 포트는 config-server 작업으로만 바꾸고, 그 작업이 성공한 것을
 * 확인한 뒤에만 port_requests·pod_external_ports 를 바꾼다. 바꿀 포트 목록은 {@code changeRequest}에 있다.
 * 상태 전이는 {@link PortOperationStatus} 참고.
 */
@Entity
@Getter
@Table(name = "port_operations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortOperation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "port_operation_id")
    private Long portOperationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PortOperationStatus status = PortOperationStatus.PROCESSING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_request_id", nullable = false)
    private ChangeRequest changeRequest;

    /** 작업을 등록할 때의 컨테이너. 끝났을 때 신청의 컨테이너가 그대로인지 이 값으로 확인한다. */
    @Column(name = "pod_name", nullable = false)
    private String podName;

    /** 등록한 작업의 번호. 같은 번호로 조회되는 다른 작업의 결과와 가리는 데 쓴다. */
    @Column(name = "job_id")
    private Long jobId;

    /** 실패한 작업의 오류 코드(config-server 의 error_code). */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    public PortOperation(ChangeRequest changeRequest, Request request, User admin) {
        this.changeRequest = changeRequest;
        this.request = request;
        this.podName = request.getPodName();
        this.requestedBy = admin;
    }

    public void registered(Long jobId) {
        this.jobId = jobId;
    }

    public void markApplied() {
        requireProcessing();
        this.status = PortOperationStatus.APPLIED;
    }

    public void markFailed(String errorCode) {
        requireProcessing();
        this.status = PortOperationStatus.FAILED;
        this.errorCode = errorCode;
    }

    public boolean isProcessing() {
        return status == PortOperationStatus.PROCESSING;
    }

    private void requireProcessing() {
        if (!isProcessing()) {
            throw new BusinessException("반영 중인 포트 작업이 아닙니다.", ErrorCode.INVALID_REQUEST_STATUS);
        }
    }
}
