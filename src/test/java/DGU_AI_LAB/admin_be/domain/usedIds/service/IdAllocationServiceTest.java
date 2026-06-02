package DGU_AI_LAB.admin_be.domain.usedIds.service;

import DGU_AI_LAB.admin_be.domain.usedIds.entity.CounterKey;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.IdCounter;
import DGU_AI_LAB.admin_be.domain.usedIds.repository.IdCounterRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdAllocationServiceTest {

    @Mock
    private IdCounterRepository idCounterRepository;

    @InjectMocks
    private IdAllocationService idAllocationService;

    @Nested
    @DisplayName("allocateNewGid")
    class AllocateNewGidTest {

        @Test
        @DisplayName("카운터가 존재하면 GID를 할당하고 카운터를 증가시킨다")
        void success() {
            IdCounter counter = IdCounter.builder()
                    .key(CounterKey.SHARED_GID)
                    .nextValue(2000L)
                    .minValue(2000L)
                    .maxValue(65535L)
                    .build();

            when(idCounterRepository.findByKey(CounterKey.SHARED_GID))
                    .thenReturn(Optional.of(counter));
            when(idCounterRepository.saveAndFlush(any(IdCounter.class)))
                    .thenReturn(counter);

            Long gid = idAllocationService.allocateNewGid();

            assertThat(gid).isEqualTo(2000L);
            assertThat(counter.getNextValue()).isEqualTo(2001L);
        }

        @Test
        @DisplayName("카운터가 없으면 GID_ALLOCATION_FAILED 예외를 던진다")
        void counterNotFound_throwsException() {
            when(idCounterRepository.findByKey(CounterKey.SHARED_GID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> idAllocationService.allocateNewGid())
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.GID_ALLOCATION_FAILED);
        }

        @Test
        @DisplayName("GID 범위가 소진되면 NO_AVAILABLE_RESOURCES 예외를 던진다")
        void maxExceeded_throwsException() {
            IdCounter counter = IdCounter.builder()
                    .key(CounterKey.SHARED_GID)
                    .nextValue(65536L)
                    .minValue(2000L)
                    .maxValue(65535L)
                    .build();

            when(idCounterRepository.findByKey(CounterKey.SHARED_GID))
                    .thenReturn(Optional.of(counter));

            assertThatThrownBy(() -> idAllocationService.allocateNewGid())
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NO_AVAILABLE_RESOURCES);
        }

        @Test
        @DisplayName("연속 할당 시 GID가 순차적으로 증가한다")
        void sequential_incrementsCorrectly() {
            IdCounter counter = IdCounter.builder()
                    .key(CounterKey.SHARED_GID)
                    .nextValue(2000L)
                    .minValue(2000L)
                    .maxValue(65535L)
                    .build();

            when(idCounterRepository.findByKey(CounterKey.SHARED_GID))
                    .thenReturn(Optional.of(counter));
            when(idCounterRepository.saveAndFlush(any(IdCounter.class)))
                    .thenReturn(counter);

            Long first = idAllocationService.allocateNewGid();
            Long second = idAllocationService.allocateNewGid();
            Long third = idAllocationService.allocateNewGid();

            assertThat(first).isEqualTo(2000L);
            assertThat(second).isEqualTo(2001L);
            assertThat(third).isEqualTo(2002L);
        }
    }
}
