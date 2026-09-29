package DGU_AI_LAB.admin_be.domain.requests.entity;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Request extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "usage_purpose", nullable = false, length = 1000)
    private String usagePurpose;

    @Column(name = "form_answers", columnDefinition = "json", nullable = false)
    private String formAnswers;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    /**
     * 허가받은 경우 값이 존재
     */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /**
     * 거절 사유 등, status에 대한 설명
     */
    @Column(name = "admin_comment", length = 300)
    private String adminComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "pod_name", length = 255)
    private String podName;

    @Column(name = "node_name", length = 100)
    private String nodeName;

    /**
     * 이 신청 번호로 마지막에 등록한 작업의 번호 — config-server operation_log의 START 행 id.
     * 작업 결과는 신청 번호로만 조회되므로, 다시 등록한 직후에는 이전 작업의 결과가 보일 수 있다 — 결과 폴러는
     * 이 번호의 결과만 반영한다. 한 신청에 동시에 도는 작업은 하나뿐이라 칸 하나로 충분하다.
     *
     * <ul>
     *   <li>PROCESSING: 생성 작업, MIGRATING: 마이그레이션 작업, EXPIRING: 컨테이너 회수 작업</li>
     *   <li>DELETED: 계정 회수 작업. DELETED로 넘어갈 때 비우므로, 값이 있으면 이 신청 번호로 계정 회수를
     *       등록했다는 뜻이다(계정 회수는 그 노드에서 계정을 마지막으로 쓴 신청 번호로 등록한다).</li>
     * </ul>
     */
    @Column(name = "job_id")
    private Long jobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rsgroup_id", nullable = false)
    private ResourceGroup resourceGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false)
    private ContainerImage containerImage;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RequestGroup> requestGroups = new LinkedHashSet<>();

    /** 신청 시 고른 noVNC 활성화 여부. config-server에 그대로 전달되어 Pod에 ENABLE_VNC로 주입된다. */
    @Column(name = "enable_vnc", nullable = false)
    private boolean enableVnc = false;

    @Builder
    public Request(LocalDateTime expiresAt, String usagePurpose, String formAnswers, User user, ResourceGroup resourceGroup, ContainerImage containerImage, boolean enableVnc) {
        this.enableVnc = enableVnc;
        this.expiresAt = expiresAt;
        this.usagePurpose = usagePurpose;
        this.formAnswers = formAnswers;
        this.user = user;
        this.resourceGroup = resourceGroup;
        this.containerImage = containerImage;
    }

    // ==== 비즈니스 메서드 ====

    /**
     * 변경 요청을 반영하여 엔티티의 속성을 업데이트합니다.
     */

    public void updateExpiresAt(LocalDateTime newExpiresAt) {
        if (newExpiresAt != null) {
            this.expiresAt = newExpiresAt;
        }
    }

    public void updateResourceGroup(ResourceGroup newResourceGroup) {
        if (newResourceGroup != null) {
            this.resourceGroup = newResourceGroup;
        }
    }

    public void updateContainerImage(ContainerImage newImage) {
        if (newImage != null) {
            this.containerImage = newImage;
        }
    }

    public void markAsProcessing() {
        transitionTo(Status.PROCESSING, "대기 중인 신청만 승인할 수 있습니다.");
        this.jobId = null;
    }

    public void recordJob(Long jobId) {
        this.jobId = jobId;
    }

    /** 계정 회수 작업을 다시 등록하게 한다(관리자가 계정 회수를 재시도할 때). DELETED 신청에만 쓴다. */
    public void forgetAccountRevokeJob() {
        if (this.status != Status.DELETED) {
            throw new BusinessException("정리가 끝난 신청만 계정 회수 작업을 다시 등록할 수 있습니다.", ErrorCode.INVALID_REQUEST_STATUS);
        }
        this.jobId = null;
    }

    public void revertToPending() {
        transitionTo(Status.PENDING, "처리 중인 신청만 대기 상태로 되돌릴 수 있습니다.");
        // podName/nodeName은 보상 트랜잭션이 이미 지운 리소스를 가리키므로 함께 지운다.
        // UID는 신청이 아니라 User에 영구히 귀속되므로 여기서 다룰 것이 없다.
        this.podName = null;
        this.nodeName = null;
        this.jobId = null;
    }

    /**
     * 제안 시스템(v2.0) 승인: 작업을 등록하는 시점에 관리자가 고른 값만 먼저 신청에 남긴다.
     * 계정·컨테이너는 config-server의 제어기가 뒤에서 만들고, 그 작업이 성공해야
     * {@link #completeApproval()}로 FULFILLED가 된다. 상태를 여기서 바꾸지 않는 이유는,
     * 아직 컨테이너가 없는 신청이 승인 완료로 보이면 안 되기 때문이다.
     */
    public void prepareAsyncApproval(ContainerImage image, ResourceGroup resourceGroup, String adminComment) {
        this.containerImage = image;
        this.resourceGroup = resourceGroup;

        if (adminComment != null && !adminComment.isBlank()) {
            this.adminComment = adminComment;
        }
    }

    /** 등록해 둔 생성 작업이 성공했을 때 승인을 확정한다. 관리자가 고른 값은 이미 반영돼 있다. */
    public void completeApproval() {
        transitionTo(Status.FULFILLED, "처리 중인 신청만 승인을 확정할 수 있습니다.");
        this.approvedAt = LocalDateTime.now();
    }

    public void reject(String comment) {
        transitionTo(Status.DENIED, "대기 또는 처리 중인 신청만 거절할 수 있습니다.");
        this.adminComment = comment;
    }

    /**
     * 사용자의 변경 요청을 엔티티에 반영합니다.
     */
    public void update(LocalDateTime newExpiresAt, String reason) {
        // 변경 요청은 FULFILLED 상태에서만 가능
        if (this.status != Status.FULFILLED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }
        // null이 아닐 때만 업데이트
        if (newExpiresAt != null) {
            this.expiresAt = newExpiresAt;
        }

        this.adminComment = "사용자 변경 요청: " + reason;

    }

    public void assignPodInfo(String podName, String nodeName) {
        this.podName = podName;
        this.nodeName = nodeName;
    }

    /**
     * Pod 마이그레이션 시작을 위해 FULFILLED -> MIGRATING으로 전환한다.
     * 행 잠금 조회(findByIdForUpdate)와 같은 트랜잭션에서 호출해야
     * 동시에 들어온 두 번째 마이그레이션 요청이 이 상태 검증에서 실제로 막힌다.
     */
    public void beginMigration() {
        transitionTo(Status.MIGRATING, "이미 마이그레이션이 진행 중이거나 처리 가능한 상태가 아닙니다.");
        this.jobId = null;
    }

    /**
     * 마이그레이션 시도가 끝나면(성공/스킵/실패 모두) MIGRATING -> FULFILLED로 되돌린다.
     */
    public void endMigration() {
        transitionTo(Status.FULFILLED, "마이그레이션 중인 신청이 아닙니다.");
    }

    /**
     * 인프라(Pod/우분투 계정) 정리 시작을 위해 FULFILLED -> EXPIRING으로 전환한다.
     * beginMigration과 마찬가지로 행 잠금 조회(findByIdForUpdate)와 같은 트랜잭션에서
     * 호출해야 동시에 들어온 두 번째 정리 시도가 이 상태 검증에서 실제로 막힌다.
     *
     * 정리 도중임을 PROCESSING으로 표현하면 안 된다 — RequestSchedulerService의 재조정
     * 잡이 오래된 PROCESSING을 PENDING으로 되돌리기 때문에, Pod/계정이 삭제되는 중인
     * 요청이 재승인 가능한 상태로 되살아난다.
     */
    public void beginExpiry() {
        transitionTo(Status.EXPIRING, "이미 정리가 진행 중이거나 정리 가능한 상태가 아닙니다.");
        this.jobId = null;
    }

    /**
     * 인프라 정리에 실패하면 EXPIRING -> FULFILLED로 되돌린다.
     * 되돌려야 다음 만료 스케줄 실행에서 다시 정리 대상(FULFILLED)으로 잡혀 재시도된다.
     * 작업 번호는 남겨 둔다 — 실패한 회수 작업의 기록이다. 다음 회수 시작(beginExpiry)이 비운다.
     */
    public void endExpiry() {
        transitionTo(Status.FULFILLED, "정리 중인 신청이 아닙니다.");
    }

    public void addGroup(Group group) {
        Long rid = this.getRequestId();
        if (rid == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        RequestGroup rg = RequestGroup.builder()
                .request(this)
                .group(group)
                .build();

        this.requestGroups.add(rg);
    }

    /**
     * 이 신청이 요구한 그룹에서 하나를 뺀다. 컨테이너를 다시 만들 때 신청 그룹도 함께 보내므로
     * (AcceptInfoResponseDTO), 계정에서 뺀 그룹을 여기 남겨 두면 재생성 때 되살아난다.
     */
    public void removeGroup(Long groupId) {
        this.requestGroups.removeIf(rg -> rg.getGroup().getGroupId().equals(groupId));
    }

    /**
     * Request의 상태를 DELETED로 변경합니다. (soft delete)
     * PENDING, DENIED 상태에서만 호출 가능합니다.
     * FULFILLED 상태의 요청은 인프라 정리 후 deleteAfterCleanup()을 사용하세요.
     */
    public void delete() {
        if (this.status == Status.DELETED) {
            throw new BusinessException("이미 삭제된 요청입니다.", ErrorCode.INVALID_REQUEST_STATUS);
        }
        if (this.status == Status.FULFILLED || this.status == Status.MIGRATING || this.status == Status.EXPIRING) {
            throw new BusinessException("컨테이너가 실행 중입니다. 인프라 정리 후 삭제해주세요.", ErrorCode.INVALID_REQUEST_STATUS);
        }
        if (this.status == Status.PROCESSING) {
            // 승인 처리(AD 계정/Pod 생성)가 백그라운드에서 진행 중인 요청을 여기서
            // 삭제하면, 처리가 끝난 뒤 DB에 전혀 추적되지 않는 고아 계정/Pod가 생긴다.
            throw new BusinessException("요청이 처리 중입니다. 처리가 완료된 후 다시 시도해주세요.", ErrorCode.INVALID_REQUEST_STATUS);
        }
        transitionTo(Status.DELETED, "삭제할 수 없는 상태입니다.");
        this.jobId = null;
    }

    /**
     * 인프라(Pod, 우분투 계정) 정리가 완료된 이후 요청을 DELETED로 전환합니다.
     * 반드시 외부 리소스 정리를 완료한 시스템 서비스(만료 처리, 사용자 삭제 등)에서만 호출하세요.
     *
     * FULFILLED가 아니라 EXPIRING을 요구한다 — 호출자가 정리를 시작하기 전에 행을 잠그고
     * beginExpiry()로 선점했어야만 여기까지 올 수 있다는 뜻이다. FULFILLED를 허용하면
     * 아무 잠금 없이 인프라를 지운 뒤 이 메서드를 부르는 경로가 검증을 통과해버린다.
     */
    public void deleteAfterCleanup() {
        // DELETED로 가는 전이는 PENDING·DENIED(취소)에도 열려 있으므로 여기서 EXPIRING을 따로 요구한다.
        if (this.status != Status.EXPIRING) {
            throw new BusinessException("인프라 정리 후 삭제는 EXPIRING 상태에서만 가능합니다.", ErrorCode.INVALID_REQUEST_STATUS);
        }
        // podName/nodeName은 어느 노드에서 운영됐는지 이력 조회에 쓰이므로 남겨둔다.
        transitionTo(Status.DELETED, "인프라 정리 후 삭제는 EXPIRING 상태에서만 가능합니다.");
        this.jobId = null;
    }

    /** 신청 유저네임은 웹 계정에 한 번 정해지면 바뀌지 않는 값이라 따로 저장하지 않고 소유자에게서 읽는다. */
    public String getUbuntuUsername() {
        return user.getUbuntuUsername();
    }

    /** UID/GID는 사람에게 영구히 귀속된다(User). 신청은 소유자의 값을 그대로 보여 준다. */
    public Long getUbuntuUid() {
        return user.getUbuntuUid();
    }

    public Long getUbuntuGid() {
        return user.getUbuntuGid();
    }

    /**
     * 상태를 바꾸는 유일한 자리. 허용 여부는 {@link Status#canTransitionTo}의 표 하나가 정한다 —
     * 공개 메서드마다 출발 상태를 따로 검사하면 빠뜨린 메서드가 표에 없는 전이를 만든다.
     */
    private void transitionTo(Status target, String deniedMessage) {
        if (!this.status.canTransitionTo(target)) {
            throw new BusinessException(deniedMessage, ErrorCode.INVALID_REQUEST_STATUS);
        }
        this.status = target;
    }

    /**
     * Lombok의 requestId 기반 @EqualsAndHashCode를 쓰지 않는다 — IDENTITY 채번이라 저장 전엔
     * requestId가 null이고, 저장 후 값이 채워지면 hashCode가 바뀐다. RequestGroup의
     * equals/hashCode가 request 필드를 포함하므로(RequestGroup.java), 저장 전에
     * originalRequest.getRequestGroups()(Hibernate가 관리하는 Set) 같은 해시 기반 컬렉션에
     * 담긴 RequestGroup은 저장 후 버킷 위치가 어긋나 조회/삭제가 안 되는 문제가 생긴다.
     * hashCode는 생애주기 내내 상수로 고정하고(equals가 그 계약을 지키는 한 문제 없음),
     * equals는 영속 상태(requestId != null)인 두 엔티티만 ID로 비교한다 — 미영속 상태끼리는
     * 값이 같아도 다른 엔티티로 취급한다(참조 동일성과 동치, this==o에서 이미 처리됨).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Request other)) return false;
        return requestId != null && requestId.equals(other.requestId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
