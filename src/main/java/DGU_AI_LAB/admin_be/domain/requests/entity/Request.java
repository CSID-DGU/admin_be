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
@EqualsAndHashCode(of = "ubuntuUsername", callSuper = false)
public class Request extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "ubuntu_username", nullable = false, length = 100, unique = true)
    private String ubuntuUsername;

    @Column(name = "ubuntu_uid", unique = true)
    private Long ubuntuUid;

    @Column(name = "ubuntu_gid")
    private Long ubuntuGid;

    @Column(name = "ubuntu_password", nullable = false)
    private String ubuntuPassword;

    @Column(name = "volume_size_GiB", nullable = false)
    private Long volumeSizeGiB;

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
    public Request(String ubuntuUsername, String ubuntuPassword, Long volumeSizeGiB, LocalDateTime expiresAt, String usagePurpose, String formAnswers, User user, ResourceGroup resourceGroup, ContainerImage containerImage) {
        this.ubuntuUsername = ubuntuUsername;
        this.ubuntuPassword = ubuntuPassword;
        this.volumeSizeGiB = volumeSizeGiB;
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

    public void updateVolumeSize(Long newVolumeSize) {
        if (newVolumeSize != null) {
            this.volumeSizeGiB = newVolumeSize;
        }
    }

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
        // 보상 트랜잭션으로 계정/Pod가 이미 정리된 뒤에만 호출된다. ubuntu_uid는 unique
        // 제약이 걸려 있는데, 계정 삭제로 풀린 UID는 나중에 다른 사용자에게 재할당될 수
        // 있다 — 여기서 지우지 않으면 그 UID를 받은 다른 요청의 승인이 제약 위반으로
        // 실패한다. podName/nodeName도 더 이상 유효한 리소스를 가리키지 않으므로 함께 지운다.
        this.ubuntuUid = null;
        this.ubuntuGid = null;
        this.podName = null;
        this.nodeName = null;
    }

    public void approve(ContainerImage image, ResourceGroup resourceGroup, Long volumeSizeGiB, String adminComment) {
        this.containerImage = image;
        this.resourceGroup = resourceGroup;
        if (volumeSizeGiB != null) {
            this.volumeSizeGiB = volumeSizeGiB;
        }
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
    public void update(Long newVolumeSizeGiB, LocalDateTime newExpiresAt, String reason) {
        // 변경 요청은 FULFILLED 상태에서만 가능
        if (this.status != Status.FULFILLED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }
        // null이 아닐 때만 업데이트
        if (newVolumeSizeGiB != null) {
            this.volumeSizeGiB = newVolumeSizeGiB;
        }
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
        if (this.status == Status.FULFILLED || this.status == Status.MIGRATING) {
            throw new BusinessException("컨테이너가 실행 중입니다. 인프라 정리 후 삭제해주세요.", ErrorCode.INVALID_REQUEST_STATUS);
        }
        this.status = Status.DELETED;
        this.ubuntuUid = null;
        this.ubuntuGid = null;
    }

    /**
     * 인프라(Pod, 우분투 계정) 정리가 완료된 이후 FULFILLED 요청을 DELETED로 전환합니다.
     * 반드시 외부 리소스 정리를 완료한 시스템 서비스(만료 처리, 사용자 삭제 등)에서만 호출하세요.
     */
    public void deleteAfterCleanup() {
        if (this.status != Status.FULFILLED) {
            throw new BusinessException("인프라 정리 후 삭제는 FULFILLED 상태에서만 가능합니다.", ErrorCode.INVALID_REQUEST_STATUS);
        }
        this.status = Status.DELETED;
        // ubuntu_uid는 unique 제약이 걸려 있다. 계정이 삭제되면 그 UID는 재사용 가능해지는데,
        // 여기서 지우지 않으면 DELETED로 끝난 과거 요청이 그 UID를 영구히 붙잡아, 나중에
        // 같은 UID를 받은 다른 사용자의 승인이 uk_requests_ubuntu_uid 위반으로 실패한다.
        // podName/nodeName은 어느 노드에서 운영됐는지 이력 조회에 쓰일 수 있어 남겨둔다.
        this.ubuntuUid = null;
        this.ubuntuGid = null;
    }

}
