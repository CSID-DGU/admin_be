package DGU_AI_LAB.admin_be.domain.requests.entity;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
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
    @Column(name = "comment", length = 300)
    private String comment;

    /**
     * 사용자가 볼륨 수정 요청 시 담아두는 값
     */
    @Column(name = "requested_volume_size_byte")
    private Long requestedVolumeSizeByte;

    /**
     * 사용자가 만료일 수정 요청 시 담아두는 값
     */
    @Column(name = "requested_expires_at")
    private LocalDateTime requestedExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rsgroup_id", nullable = false)
    private ResourceGroup resourceGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "image_name", referencedColumnName = "image_name", nullable = false),
            @JoinColumn(name = "image_version", referencedColumnName = "image_version", nullable = false)
    })
    private ContainerImage containerImage;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.Set<RequestGroup> requestGroups = new java.util.LinkedHashSet<>();

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
