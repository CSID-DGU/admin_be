package DGU_AI_LAB.admin_be.domain.containerImage.entity;

import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
// 운영 DB에는 ddl-auto가 아니라 직접 DDL로 같은 이름의 제약을 걸었다(2026-09-25).
@Table(name = "container_image", uniqueConstraints = @UniqueConstraint(
        name = "uk_container_image_name_version", columnNames = {"image_name", "image_version"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContainerImage extends BaseTimeEntity  {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

    @Column(name = "image_name", length = 100, nullable = false)
    private String imageName;

    @Column(name = "image_version", length = 100, nullable = false)
    private String imageVersion;

    @Column(name = "cuda_version", length = 100, nullable = false)
    private String cudaVersion;

    @Column(name = "description", length = 500, nullable = false)
    private String description;

    @Builder
    public ContainerImage(String imageName, String imageVersion, String cudaVersion, String description) {
        this.imageName = imageName;
        this.imageVersion = imageVersion;
        this.cudaVersion = cudaVersion;
        this.description = description;
    }
}
