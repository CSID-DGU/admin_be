package DGU_AI_LAB.admin_be.domain.users.entity;

import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
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
public class UsedId extends BaseTimeEntity {
    @Id
    @Column(name = "uid")
    private Long uid;
}
