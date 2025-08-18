package DGU_AI_LAB.admin_be.domain.containerImage.entity;

import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "container_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
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
}
