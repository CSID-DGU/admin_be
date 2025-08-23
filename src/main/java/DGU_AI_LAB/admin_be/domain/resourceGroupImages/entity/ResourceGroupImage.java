package DGU_AI_LAB.admin_be.domain.resourceGroupImages.entity;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "resource_group_images")
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ResourceGroupImage extends BaseTimeEntity {

    @EmbeddedId
    private ResourceGroupImageId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("rsgroupId")
    @JoinColumn(name = "rsgroup_id", nullable = false)
    private ResourceGroup resourceGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("imageId")
    @JoinColumn(name = "image_id", nullable = false)
    private ContainerImage containerImage;

    @PrePersist
    void onCreate() {
        if (id == null && resourceGroup != null && containerImage != null) {
            id = new ResourceGroupImageId(resourceGroup.getRsgroupId(), containerImage.getImageId());
        }
    }
}