package DGU_AI_LAB.admin_be.domain.requests.entity;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
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

    @Column(name = "ubuntu_username", nullable = false, length = 100)
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

    /**
     * 허가받은 경우 값이 존재
     */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    /**
     * 거절 사유 등, status에 대한 설명
     */
    @Column(name = "admin_comment", length = 300)
    private String adminComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubuntuUid", referencedColumnName = "id_value", nullable = false)
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

    // ==== 비즈니스 로직 ====
    public void updateStatus(Status status, String comment, LocalDateTime approvedAt) {
        this.status = status;
        this.adminComment = comment;
        this.approvedAt = approvedAt;
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

    /*public void requestModification(Long newVolumeSizeByte, LocalDateTime newExpiresAt, String reason) {
        this.requestedVolumeSizeGi = newVolumeSizeByte;
        this.requestedExpiresAt = newExpiresAt;
        this.adminComment = "변경 요청: " + reason;
    }*/

    /*public void applyModification() {
        if (this.requestedVolumeSizeGi != null) {
            this.volumeSizeGiB = this.requestedVolumeSizeGi;
        }
        if (this.requestedExpiresAt != null) {
            this.expiresAt = this.requestedExpiresAt;
        }
        this.requestedVolumeSizeGi = null;
        this.requestedExpiresAt = null;
        this.adminComment = "변경 완료됨";
    }*/

    /*public void rejectModification(String reason) {
        this.requestedVolumeSizeGi = null;
        this.requestedExpiresAt = null;
        this.adminComment = "변경 요청 거절: " + reason;
    }*/

    public void addGroup(Group group) {
        RequestGroup rg = RequestGroup.builder()
                .request(this)
                .group(group)
                .build();
        this.requestGroups.add(rg);
    }

    public void removeGroup(Long ubuntuGid) {
        this.requestGroups.removeIf(rg -> rg.getGroup().getUbuntuGid().equals(ubuntuGid));
    }
}
