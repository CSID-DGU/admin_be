package DGU_AI_LAB.admin_be.domain.usedIds.entity;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group; // Group 임포트 필수
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "used_ids")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "idValue")
public class UsedId {

    @Id
    @Column(name = "id_value", nullable = false)
    private Long idValue;

    @OneToOne(mappedBy = "usedId", cascade = CascadeType.ALL, orphanRemoval = true)
    private Group group;

    @Builder
    public UsedId(Long idValue) {
        this.idValue = idValue;
    }
}