package DGU_AI_LAB.admin_be.domain.containerImage.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "container_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ContainerImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

    @Column(name = "image_name", length = 100, nullable = false)
    private String imageName;

    @Column(name = "image_version", length = 100, nullable = false)
    private String imageVersion;
}
