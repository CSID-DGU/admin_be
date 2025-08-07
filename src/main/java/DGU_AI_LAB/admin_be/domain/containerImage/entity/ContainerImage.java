package DGU_AI_LAB.admin_be.domain.containerImage.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "container_image")
@IdClass(ContainerImageId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ContainerImage {

    @Id
    @Column(name = "image_name", length = 100, nullable = false)
    private String imageName;

    @Id
    @Column(name = "image_version", length = 100, nullable = false)
    private String imageVersion;
}
