package DGU_AI_LAB.admin_be.domain.usedIds.entity;

import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdCounterTest {

    @Test
    @DisplayName("allocateOne: nextValue를 반환하고 1 증가시킨다")
    void allocateOne_returnsCurrentAndIncrements() {
        IdCounter counter = IdCounter.builder()
                .key(CounterKey.UID)
                .nextValue(10000L)
                .minValue(10000L)
                .maxValue(99999L)
                .build();

        long first = counter.allocateOne();
        long second = counter.allocateOne();

        assertThat(first).isEqualTo(10000L);
        assertThat(second).isEqualTo(10001L);
        assertThat(counter.getNextValue()).isEqualTo(10002L);
    }

    @Test
    @DisplayName("allocateOne: nextValue가 minValue보다 작으면 minValue부터 할당한다")
    void allocateOne_belowMin_startsFromMin() {
        IdCounter counter = IdCounter.builder()
                .key(CounterKey.SHARED_GID)
                .nextValue(1000L)
                .minValue(2000L)
                .maxValue(65535L)
                .build();

        long result = counter.allocateOne();

        assertThat(result).isEqualTo(2000L);
        assertThat(counter.getNextValue()).isEqualTo(2001L);
    }

    @Test
    @DisplayName("allocateOne: nextValue가 maxValue를 초과하면 예외를 던진다")
    void allocateOne_exceedsMax_throwsException() {
        IdCounter counter = IdCounter.builder()
                .key(CounterKey.SHARED_GID)
                .nextValue(65536L)
                .minValue(2000L)
                .maxValue(65535L)
                .build();

        assertThatThrownBy(counter::allocateOne)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("allocateOne: maxValue 경계값에서 정상 할당 후 다음 호출에서 예외")
    void allocateOne_atMaxBoundary_allocatesThenThrows() {
        IdCounter counter = IdCounter.builder()
                .key(CounterKey.SHARED_GID)
                .nextValue(65535L)
                .minValue(2000L)
                .maxValue(65535L)
                .build();

        long result = counter.allocateOne();
        assertThat(result).isEqualTo(65535L);

        assertThatThrownBy(counter::allocateOne)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("allocateOne: 연속 호출 시 순차적으로 증가한다")
    void allocateOne_sequential_incrementsCorrectly() {
        IdCounter counter = IdCounter.builder()
                .key(CounterKey.UID)
                .nextValue(10000L)
                .minValue(10000L)
                .maxValue(99999L)
                .build();

        for (long expected = 10000L; expected < 10010L; expected++) {
            assertThat(counter.allocateOne()).isEqualTo(expected);
        }
        assertThat(counter.getNextValue()).isEqualTo(10010L);
    }
}
