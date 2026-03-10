package DGU_AI_LAB.admin_be.domain.usedIds.entity;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "id_counter")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdCounter {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "counter_key", nullable = false)
    private CounterKey key;

    @Column(name = "min_value", nullable = false)
    private long minValue;

    @Column(name = "max_value", nullable = false)
    private long maxValue;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    @Builder
    public IdCounter(CounterKey key, long minValue, long maxValue, long nextValue) {
        this.key = key;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.nextValue = nextValue;
    }

    /**
     * 다음 ID를 할당하고 카운터를 1 증가시킵니다.
     * nextValue가 minValue보다 작으면 minValue부터 시작합니다.
     *
     * @return 할당된 ID 값
     * @throws BusinessException nextValue가 maxValue를 초과한 경우
     */
    public long allocateOne() {
        if (nextValue < minValue) {
            nextValue = minValue;
        }
        if (nextValue > maxValue) {
            throw new BusinessException(ErrorCode.NO_AVAILABLE_RESOURCES);
        }
        return nextValue++;
    }
}
