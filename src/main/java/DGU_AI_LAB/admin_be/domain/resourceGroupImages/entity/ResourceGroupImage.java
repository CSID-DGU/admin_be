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
@EqualsAndHashCode(of = "id", callSuper = false)
public class ResourceGroupImage extends BaseTimeEntity {

    @EmbeddedId
    private ResourceGroupImageId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rsgroup_id", nullable = false, insertable = false, updatable = false)
    private ResourceGroup resourceGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id", nullable = false, insertable = false, updatable = false)
    private ContainerImage containerImage;

    @Builder
    public ResourceGroupImage(ResourceGroup resourceGroup, ContainerImage containerImage) {
        this.resourceGroup = resourceGroup;
        this.containerImage = containerImage;
        if (resourceGroup != null && containerImage != null) {
            this.id = new ResourceGroupImageId(resourceGroup.getRsgroupId(), containerImage.getImageId());
        }
    }

    @PrePersist
    void onCreate() {
        if (id == null && resourceGroup != null && containerImage != null) {
            id = new ResourceGroupImageId(resourceGroup.getRsgroupId(), containerImage.getImageId());
        }
    }
}