package DGU_AI_LAB.admin_be.domain.usedIds.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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
