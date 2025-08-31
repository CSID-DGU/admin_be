package DGU_AI_LAB.admin_be.domain.usedIds.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "used_ids")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "idValue")
public class UsedId {

    @Id
    @Column(name = "id_value", nullable = false)
    private Long idValue;
}
