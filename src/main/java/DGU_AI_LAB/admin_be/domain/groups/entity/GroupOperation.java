package DGU_AI_LAB.admin_be.domain.groups.entity;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공용 그룹 작업 하나(생성·멤버 추가·제거). 그룹은 AD·계정 원장·NAS·떠 있는 컨테이너에 걸쳐 있어 config-server
 * 작업으로만 바꾸고, 그 작업이 성공한 것을 확인한 뒤에만 groups·user_groups 를 바꾼다. 상태 전이는
 * {@link GroupOperationStatus} 참고.
 *
 * <p>종류마다 쓰는 값이 다르다: 생성은 {@code groupName}, 추가는 {@code changeRequest}(더할 그룹은 그 변경 요청에
 * 있다), 제거는 {@code group}.
 */
@Entity
@Getter
@Table(name = "group_operations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupOperation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_operation_id")
    private Long groupOperationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private GroupOperationKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GroupOperationStatus status = GroupOperationStatus.PROCESSING;

    /** 그룹이 바뀌는 계정의 주인. 생성은 만든 사람이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 작업에 넘긴 리눅스 계정명. 끝났을 때 그 계정이 그대로인지 이 값으로 확인한다. 멤버 없는 생성은 null. */
    @Column(name = "ubuntu_username", length = 100)
    private String ubuntuUsername;

    @Column(name = "group_name", length = 100)
    private String groupName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "change_request_id")
    private ChangeRequest changeRequest;

    /** 등록한 작업의 번호. 같은 번호로 조회되는 다른 작업의 결과와 가리는 데 쓴다. */
    @Column(name = "job_id")
    private Long jobId;

    /** 실패한 작업의 오류 코드(config-server 의 error_code). */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    private GroupOperation(GroupOperationKind kind, User user, String ubuntuUsername, User requestedBy) {
        this.kind = kind;
        this.user = user;
        this.ubuntuUsername = ubuntuUsername;
        this.requestedBy = requestedBy;
    }

    public static GroupOperation create(User requester, String groupName, String memberUsername) {
        GroupOperation operation = new GroupOperation(GroupOperationKind.CREATE, requester, memberUsername, requester);
        operation.groupName = groupName;
        return operation;
    }

    public static GroupOperation add(ChangeRequest changeRequest, User owner, String ubuntuUsername, User admin) {
        GroupOperation operation = new GroupOperation(GroupOperationKind.ADD, owner, ubuntuUsername, admin);
        operation.changeRequest = changeRequest;
        return operation;
    }

    public static GroupOperation remove(User owner, Group group, User admin) {
        GroupOperation operation = new GroupOperation(GroupOperationKind.REMOVE, owner, owner.getUbuntuUsername(), admin);
        operation.group = group;
        operation.groupName = group.getGroupName();
        return operation;
    }

    public void registered(Long jobId) {
        this.jobId = jobId;
    }

    public void markApplied() {
        requireProcessing();
        this.status = GroupOperationStatus.APPLIED;
    }

    public void markFailed(String errorCode) {
        requireProcessing();
        this.status = GroupOperationStatus.FAILED;
        this.errorCode = errorCode;
    }

    public boolean isProcessing() {
        return status == GroupOperationStatus.PROCESSING;
    }

    private void requireProcessing() {
        if (!isProcessing()) {
            throw new BusinessException("반영 중인 그룹 작업이 아닙니다.", ErrorCode.INVALID_REQUEST_STATUS);
        }
    }
}
