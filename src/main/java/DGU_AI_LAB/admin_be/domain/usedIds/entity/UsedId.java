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

    @Column(name = "is_used", nullable = false)
    private Boolean isUsed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (isUsed == null) isUsed = true;
    }

    public static UsedId allocate(Long id) {
        return UsedId.builder()
                .idValue(id)
                .isUsed(true)
                .build();
    }

    public void release() {
        this.isUsed = false;
    }
}
