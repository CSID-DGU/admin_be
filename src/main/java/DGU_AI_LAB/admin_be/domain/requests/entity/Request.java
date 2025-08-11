package DGU_AI_LAB.admin_be.domain.requests.entity;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
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
@AllArgsConstructor
@Builder
public class Request extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "image_name", referencedColumnName = "image_name", nullable = false),
            @JoinColumn(name = "image_version", referencedColumnName = "image_version", nullable = false)
    })
    private ContainerImage containerImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rsgroup_id", nullable = false)
    private ResourceGroup resourceGroup;

    @ManyToMany
    @JoinTable(
            name = "RequestGroups",
            joinColumns = @JoinColumn(name = "request_id"),
            inverseJoinColumns = @JoinColumn(name = "ubuntu_gid")
    )
    @Builder.Default
    private Set<Group> ubuntuGroups = new LinkedHashSet<>();

    @Column(name = "ubuntu_username", nullable = false, length = 100)
    private String ubuntuUsername;

    @Column(name = "ubuntu_uid", nullable = false)
    private Long ubuntuUid;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "volume_size_byte", nullable = false)
    private Long volumeSizeByte;

    @Column(name = "cuda_version", nullable = false, length = 100)
    private String cudaVersion;

    @Column(name = "usage_purpose", nullable = false, length = 1000)
    private String usagePurpose;

    @Column(name = "form_answers", columnDefinition = "json", nullable = false)
    private String formAnswers;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "comment", length = 300)
    private String comment;

    @Column(name = "requested_volume_size_byte")
    private Long requestedVolumeSizeByte;

    @Column(name = "requested_expires_at")
    private LocalDateTime requestedExpiresAt;

    // ==== 비즈니스 로직 ====
    public void updateStatus(Status status, String comment, LocalDateTime approvedAt) {
        this.status = status;
        this.comment = comment;
        this.approvedAt = approvedAt;
    }

    public void approve(ContainerImage image, Long volumeSizeByte, String cudaVersion) {
        this.containerImage = image;
        this.volumeSizeByte = volumeSizeByte;
        this.cudaVersion = cudaVersion;
        this.status = Status.FULFILLED;
        this.approvedAt = LocalDateTime.now();
        this.comment = null;
    }

    public void reject(String comment) {
        this.status = Status.DENIED;
        this.approvedAt = LocalDateTime.now();
        this.comment = comment;
    }

    public void requestModification(Long newVolumeSizeByte, LocalDateTime newExpiresAt, String reason) {
        this.requestedVolumeSizeByte = newVolumeSizeByte;
        this.requestedExpiresAt = newExpiresAt;
        this.comment = "변경 요청: " + reason;
    }

    public void applyModification() {
        if (this.requestedVolumeSizeByte != null) {
            this.volumeSizeByte = this.requestedVolumeSizeByte;
        }
        if (this.requestedExpiresAt != null) {
            this.expiresAt = this.requestedExpiresAt;
        }
        this.requestedVolumeSizeByte = null;
        this.requestedExpiresAt = null;
        this.comment = "변경 완료됨";
    }

    public void rejectModification(String reason) {
        this.requestedVolumeSizeByte = null;
        this.requestedExpiresAt = null;
        this.comment = "변경 요청 거절: " + reason;
    }
}
