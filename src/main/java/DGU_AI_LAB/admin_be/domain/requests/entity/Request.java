package DGU_AI_LAB.admin_be.domain.requests.entity;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.nodes.entity.Node;
import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
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
    private Long requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "image_name", referencedColumnName = "image_name"),
            @JoinColumn(name = "image_version", referencedColumnName = "image_version")
    })
    private ContainerImage containerImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubuntu_gid")
    private Group ubuntuGroup;

    @Column(name = "ubuntu_username", length = 100)
    private String ubuntuUsername;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "volume_size_byte", nullable = false)
    private Long volumeSizeByte;

    @Column(name = "cuda_version", nullable = false, length = 100)
    private String cudaVersion;

    @Column(name = "usage_purpose", nullable = false, length = 1000)
    private String usagePurpose;

    @Column(name = "form_answers", columnDefinition = "JSON", nullable = false)
    private String formAnswers;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "comment", length = 300)
    private String comment;

    public void updateStatus(Status status, String comment, LocalDateTime approvedAt) {
        this.status = status;
        this.comment = comment;
        this.approvedAt = approvedAt;
    }

    public void approve(Node node, ContainerImage image, Long volumeSizeByte, String cudaVersion) {
        this.node = node;
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

    // 임시 사용 필드
    private Long requestedVolumeSizeByte;

    public void requestModification(Long newVolumeSizeByte, String reason) {
        this.requestedVolumeSizeByte = newVolumeSizeByte;
        this.comment = "변경 요청: " + reason;
    }

    public void applyModification() {
        if (this.requestedVolumeSizeByte != null) {
            this.volumeSizeByte = this.requestedVolumeSizeByte;
            this.requestedVolumeSizeByte = null;
            this.comment = "변경 완료됨";
        }
    }

    public void rejectModification(String reason) {
        this.requestedVolumeSizeByte = null;
        this.comment = "변경 요청 거절: " + reason;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "node_id", referencedColumnName = "node_id"),
            @JoinColumn(name = "rsgroup_id", referencedColumnName = "rsgroup_id")
    })
    private Node node;



}
