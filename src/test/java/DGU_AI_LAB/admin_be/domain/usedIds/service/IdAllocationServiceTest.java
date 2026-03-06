package DGU_AI_LAB.admin_be.domain.usedIds.service;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.CounterKey;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.IdCounter;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.usedIds.repository.IdCounterRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.repository.UsedIdRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdAllocationServiceTest {

    @Mock
    private UsedIdRepository usedIdRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private IdCounterRepository idCounterRepository;

    @InjectMocks
    private IdAllocationService idAllocationService;

    // ===== allocateNewGid =====

    @Nested
    @DisplayName("allocateNewGid")
    class AllocateNewGidTest {

        @Test
        @DisplayName("카운터가 존재하면 GID를 할당하고 UsedId를 저장한다")
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
            when(usedIdRepository.saveAndFlush(any(UsedId.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Long gid = idAllocationService.allocateNewGid();

            assertThat(gid).isEqualTo(2000L);
            assertThat(counter.getNextValue()).isEqualTo(2001L);
            verify(idCounterRepository).saveAndFlush(counter);
            verify(usedIdRepository).saveAndFlush(any(UsedId.class));
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
        @DisplayName("UsedId 저장 시 중복 키 충돌이면 GID_ALLOCATION_FAILED 예외를 던진다")
        void duplicateKey_throwsException() {
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
            when(usedIdRepository.saveAndFlush(any(UsedId.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() -> idAllocationService.allocateNewGid())
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.GID_ALLOCATION_FAILED);
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
            when(usedIdRepository.saveAndFlush(any(UsedId.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Long first = idAllocationService.allocateNewGid();
            Long second = idAllocationService.allocateNewGid();
            Long third = idAllocationService.allocateNewGid();

            assertThat(first).isEqualTo(2000L);
            assertThat(second).isEqualTo(2001L);
            assertThat(third).isEqualTo(2002L);
        }
    }

    // ===== allocateFor =====

    @Nested
    @DisplayName("allocateFor")
    class AllocateForTest {

        @Test
        @DisplayName("기존 UID가 있으면 재사용하고, 기존 그룹이 있으면 재사용한다")
        void reusesExistingUidAndGroup() {
            UsedId existingUid = UsedId.builder().idValue(10001L).build();
            Group existingGroup = Group.builder().groupName("testuser").ubuntuGid(10001L).build();
            Request prevRequest = mock(Request.class);
            Request request = mock(Request.class);

            when(request.getUbuntuUsername()).thenReturn("testuser");
            when(requestRepository.findTopByUbuntuUsernameAndUbuntuUidIsNotNullOrderByApprovedAtDesc("testuser"))
                    .thenReturn(Optional.of(prevRequest));
            when(prevRequest.getUbuntuUid()).thenReturn(existingUid);
            when(groupRepository.findById(10001L)).thenReturn(Optional.of(existingGroup));

            IdAllocationService.AllocationResult result = idAllocationService.allocateFor(request);

            assertThat(result.getUid()).isEqualTo(existingUid);
            assertThat(result.getPrimaryGroup()).isEqualTo(existingGroup);
            verify(idCounterRepository, never()).findByKey(any());
        }

        @Test
        @DisplayName("기존 UID가 없으면 새 UID를 할당한다")
        void allocatesNewUidWhenNoneExists() {
            Request request = mock(Request.class);
            when(request.getUbuntuUsername()).thenReturn("newuser");
            when(requestRepository.findTopByUbuntuUsernameAndUbuntuUidIsNotNullOrderByApprovedAtDesc("newuser"))
                    .thenReturn(Optional.empty());

            IdCounter uidCounter = IdCounter.builder()
                    .key(CounterKey.UID)
                    .nextValue(10000L)
                    .minValue(10000L)
                    .maxValue(2147483647L)
                    .build();

            UsedId newUid = UsedId.builder().idValue(10000L).build();
            Group newGroup = Group.builder().groupName("newuser").ubuntuGid(10000L).build();

            when(idCounterRepository.findByKey(CounterKey.UID))
                    .thenReturn(Optional.of(uidCounter));
            when(idCounterRepository.saveAndFlush(any(IdCounter.class)))
                    .thenReturn(uidCounter);
            when(usedIdRepository.saveAndFlush(any(UsedId.class)))
                    .thenReturn(newUid);
            when(groupRepository.findById(10000L))
                    .thenReturn(Optional.empty());
            when(groupRepository.saveAndFlush(any(Group.class)))
                    .thenReturn(newGroup);

            IdAllocationService.AllocationResult result = idAllocationService.allocateFor(request);

            assertThat(result.getUid().getIdValue()).isEqualTo(10000L);
            assertThat(result.getPrimaryGroup().getGroupName()).isEqualTo("newuser");
            verify(idCounterRepository).findByKey(CounterKey.UID);
            verify(idCounterRepository).saveAndFlush(uidCounter);
        }

        @Test
        @DisplayName("UID 카운터가 없으면 UID_ALLOCATION_FAILED 예외를 던진다")
        void noUidCounter_throwsException() {
            Request request = mock(Request.class);
            when(request.getUbuntuUsername()).thenReturn("newuser");
            when(requestRepository.findTopByUbuntuUsernameAndUbuntuUidIsNotNullOrderByApprovedAtDesc("newuser"))
                    .thenReturn(Optional.empty());
            when(idCounterRepository.findByKey(CounterKey.UID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> idAllocationService.allocateFor(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UID_ALLOCATION_FAILED);
        }
    }

    // ===== releaseId =====

    @Nested
    @DisplayName("releaseId")
    class ReleaseIdTest {

        @Test
        @DisplayName("UsedId를 정상적으로 삭제한다")
        void success() {
            UsedId usedId = UsedId.builder().idValue(10001L).build();

            idAllocationService.releaseId(usedId);

            verify(usedIdRepository).delete(usedId);
        }

        @Test
        @DisplayName("null이 전달되면 삭제를 건너뛴다")
        void nullInput_skips() {
            idAllocationService.releaseId(null);

            verify(usedIdRepository, never()).delete(any());
        }

        @Test
        @DisplayName("삭제 실패 시 USED_ID_RELEASE_FAILED 예외를 던진다")
        void deleteFails_throwsException() {
            UsedId usedId = UsedId.builder().idValue(10001L).build();
            doThrow(new RuntimeException("DB error"))
                    .when(usedIdRepository).delete(usedId);

            assertThatThrownBy(() -> idAllocationService.releaseId(usedId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USED_ID_RELEASE_FAILED);
        }
    }
}
