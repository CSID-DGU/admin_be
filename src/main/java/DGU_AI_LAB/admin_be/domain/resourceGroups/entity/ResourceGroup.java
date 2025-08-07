package DGU_AI_LAB.admin_be.domain.resourceGroups.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "resource_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ResourceGroup {

    @Id
    @Column(name = "rsgroup_id", nullable = false)
    private Long rsgroupId;

    @Column(name = "description", length = 500)
    private String description;
}
