package DGU_AI_LAB.admin_be.domain.resourceGroups.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "resource_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "rsgroupId")
public class ResourceGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rsgroup_id", nullable = false)
    private Integer rsgroupId;

    @Column(name = "resource_group_name", length = 300)
    private String resourceGroupName; // ex. 3090ti ...

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "server_name", length = 300)
    private String serverName; // ex. FARM, LAB

    @Builder
    public ResourceGroup(Integer rsgroupId, String resourceGroupName, String description, String serverName) {
        this.rsgroupId = rsgroupId;
        this.resourceGroupName = resourceGroupName;
        this.description = description;
        this.serverName = serverName;
    }
}
