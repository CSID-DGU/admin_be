package DGU_AI_LAB.admin_be.domain.image.entity;

import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Image extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long imageId;

    @Column(name = "imageVersion", nullable = false)
    private String imageVersion;

    @Column(name = "imageTag", nullable = false)
    private String imageTag;
}
