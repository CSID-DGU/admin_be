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
@EqualsAndHashCode(of = "requestId", callSuper = false)
public class Request extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    /**
     * 신청 시점에 User.ubuntuUsername에서 복사해 오는 비정규화 사본 — 신청마다 따로 고르는
     * 값이 아니다. 같은 사용자의 신청들은 모두 같은 값을 갖고, 지난 신청 이력에도 남으므로
     * 더 이상 unique가 아니다. 유일성은 User.ubuntuUsername이 책임진다.
     */
    @Column(name = "ubuntu_username", nullable = false, length = 100)
    private String ubuntuUsername;

    /** User에 귀속된 UID/GID의 사본(이력 조회용). 실제 소유자는 User다. */
    @Column(name = "ubuntu_uid")
    private Long ubuntuUid;

    @Column(name = "ubuntu_gid")
    private Long ubuntuGid;

    @Column(name = "ubuntu_password", nullable = false)
    private String ubuntuPassword;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rsgroup_id", nullable = false)
    private ResourceGroup resourceGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false)
    private ContainerImage containerImage;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RequestGroup> requestGroups = new LinkedHashSet<>();

    @Builder
    public Request(String ubuntuUsername, String ubuntuPassword, LocalDateTime expiresAt, String usagePurpose, String formAnswers, User user, ResourceGroup resourceGroup, ContainerImage containerImage) {
        this.ubuntuUsername = ubuntuUsername;
        this.ubuntuPassword = ubuntuPassword;
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
        this.status = Status.PROCESSING;
    }

    public void revertToPending() {
        this.status = Status.PENDING;
        // podName/nodeName은 보상 트랜잭션이 이미 지운 리소스를 가리키므로 함께 지운다.
        // ubuntuUid/ubuntuGid는 건드리지 않는다 — 이제 UID는 신청이 아니라 User에 귀속되고,
        // 이 신청 하나가 실패했다고 사용자의 리눅스 계정이 사라지는 것은 아니다.
        // 계정까지 실제로 삭제된 경우의 UID 회수는 User.releaseUbuntuAccount()가 담당한다.
        this.podName = null;
        this.nodeName = null;
    }

    public void approve(ContainerImage image, ResourceGroup resourceGroup, String adminComment) {
        this.containerImage = image;
        this.resourceGroup = resourceGroup;
        this.status = Status.FULFILLED;
        this.approvedAt = LocalDateTime.now();

        if (adminComment != null && !adminComment.isBlank()) {
            this.adminComment = adminComment;
        }
    }

    public void reject(String comment) {
        this.status = Status.DENIED;
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
     * infra(config-server) 계정 생성/조회 API가 요구하는 Base64 포맷으로 변환한다.
     * DB에는 평문 한 벌만 보관하고, 전송 시점에만 인코딩해서 이중 저장을 피한다.
     */
    public String getUbuntuPasswordBase64() {
        return java.util.Base64.getEncoder().encodeToString(this.ubuntuPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Pod 마이그레이션 시작을 위해 FULFILLED -> MIGRATING으로 전환한다.
     * 행 잠금 조회(findByIdForUpdate)와 같은 트랜잭션에서 호출해야
     * 동시에 들어온 두 번째 마이그레이션 요청이 이 상태 검증에서 실제로 막힌다.
     */
    public void beginMigration() {
        if (this.status != Status.FULFILLED) {
            throw new BusinessException("이미 마이그레이션이 진행 중이거나 처리 가능한 상태가 아닙니다.", ErrorCode.INVALID_REQUEST_STATUS);
        }
        this.status = Status.MIGRATING;
    }

    /**
     * 마이그레이션 시도가 끝나면(성공/스킵/실패 모두) MIGRATING -> FULFILLED로 되돌린다.
     */
    public void endMigration() {
        if (this.status != Status.MIGRATING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }
        this.status = Status.FULFILLED;
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
        if (this.status != Status.FULFILLED) {
            throw new BusinessException("이미 정리가 진행 중이거나 정리 가능한 상태가 아닙니다.", ErrorCode.INVALID_REQUEST_STATUS);
        }
        this.status = Status.EXPIRING;
    }

    /**
     * 인프라 정리에 실패하면 EXPIRING -> FULFILLED로 되돌린다.
     * 되돌려야 다음 만료 스케줄 실행에서 다시 정리 대상(FULFILLED)으로 잡혀 재시도된다.
     */
    public void endExpiry() {
        if (this.status != Status.EXPIRING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }
        this.status = Status.FULFILLED;
    }

    public void assignUbuntuIds(Long ubuntuUid, Long ubuntuGid) {
        if (ubuntuUid == null || ubuntuGid == null || ubuntuUid <= 0 || ubuntuGid <= 0) {
            throw new BusinessException(ErrorCode.UID_ALLOCATION_FAILED);
        }
        this.ubuntuUid = ubuntuUid;
        this.ubuntuGid = ubuntuGid;
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
        this.status = Status.DELETED;
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
        if (this.status != Status.EXPIRING) {
            throw new BusinessException("인프라 정리 후 삭제는 EXPIRING 상태에서만 가능합니다.", ErrorCode.INVALID_REQUEST_STATUS);
        }
        // ubuntuUid/ubuntuGid/podName/nodeName은 어떤 계정으로 어느 노드에서 운영됐는지
        // 이력 조회에 쓰이므로 남겨둔다. 신청 하나가 정리됐다고 사용자의 리눅스 계정이
        // 삭제되는 것은 아니므로 여기서 지울 이유도 없다.
        this.status = Status.DELETED;
    }

}
