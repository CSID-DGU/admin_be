package DGU_AI_LAB.admin_be.domain.resourceGroupImages.entity;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ResourceGroupImageId implements Serializable {

    @Column(name = "rsgroup_id")
    private Integer rsgroupId;

    @Column(name = "image_id")
    private Long imageId;
}