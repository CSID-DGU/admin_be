package DGU_AI_LAB.admin_be.domain.requests.entity;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Request extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "ubuntu_username", nullable = false, length = 100, unique = true)
    private String ubuntuUsername;

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
    @Builder.Default
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubuntuUid", referencedColumnName = "id_value", nullable = true)
    private UsedId ubuntuUid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rsgroup_id", nullable = false)
    private ResourceGroup resourceGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false)
    private ContainerImage containerImage;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.Set<RequestGroup> requestGroups = new java.util.LinkedHashSet<>();


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

    public void approve(ContainerImage image, ResourceGroup resourceGroup, Long volumeSizeGiB, LocalDateTime expiresAt, String adminComment) {
        this.containerImage = image;
        this.resourceGroup = resourceGroup;
        this.volumeSizeGiB = volumeSizeGiB;
        this.expiresAt = expiresAt;
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

    public void assignUbuntuUid(UsedId uid) {
        this.ubuntuUid = uid;
    }

    public void addGroup(Group group) {
        Long rid = this.getRequestId();
        if (rid == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        RequestGroup rg = RequestGroup.builder()
                .id(new RequestGroupId(rid, group.getUbuntuGid()))
                .request(this)
                .group(group)
                .build();

        this.requestGroups.add(rg);
    }

    /**
     * Request의 상태를 DELETED로 변경합니다. (soft delete)
     */
    public void delete() {
        if (this.status == Status.DELETED) {
            throw new BusinessException("이미 삭제된 요청입니다.", ErrorCode.INVALID_REQUEST_STATUS);
        }
        this.status = Status.DELETED;
    }

}
