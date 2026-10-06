package DGU_AI_LAB.admin_be.domain.home.service;

import DGU_AI_LAB.admin_be.domain.home.entity.HomeCleanup;
import DGU_AI_LAB.admin_be.domain.home.entity.HomeCleanupStatus;
import DGU_AI_LAB.admin_be.domain.home.repository.HomeCleanupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HomeCleanupService")
class HomeCleanupServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long CLEANUP_ID = 3L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 9, 30);
    /** 기본 보존 기간(40일)이 지난 종료 시각. */
    private static final LocalDateTime ENDED_LONG_AGO = NOW.minusDays(41);

    @Mock private UserRepository userRepository;
    @Mock private HomeCleanupRepository cleanupRepository;
    @Mock private User user;

    private HomeCleanupService service;

    @BeforeEach
    void setUp() {
        service = new HomeCleanupService(userRepository, cleanupRepository, new HomeRetentionPolicy());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.getUbuntuUsername()).thenReturn("hong");
        when(user.getUbuntuUid()).thenReturn(55010L);
        when(user.hasOpenRequest()).thenReturn(false);
        when(user.lastContainerEndedAt()).thenReturn(Optional.of(ENDED_LONG_AGO));
        when(cleanupRepository.save(any(HomeCleanup.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("보존 기간이 지난 홈이면 삭제 시도를 만들고 작업 내용을 돌려준다")
    void beginCreatesAttempt() {
        Optional<HomeCleanupService.Target> target = service.begin(USER_ID, NOW);

        assertThat(target).isPresent();
        assertThat(target.get().ubuntuUsername()).isEqualTo("hong");
        assertThat(target.get().ubuntuUid()).isEqualTo(55010L);
        assertThat(target.get().lastContainerEndedAt()).isEqualTo(ENDED_LONG_AGO);
    }

    @Test
    @DisplayName("리눅스 계정을 가진 적이 없으면 만들지 않는다")
    void beginSkipsUserWithoutAccount() {
        when(user.getUbuntuUid()).thenReturn(null);

        assertThat(service.begin(USER_ID, NOW)).isEmpty();
        verify(cleanupRepository, never()).save(any());
    }

    @Test
    @DisplayName("후보 조회 뒤 새로 신청했으면 만들지 않는다")
    void beginSkipsUserWithOpenRequest() {
        when(user.hasOpenRequest()).thenReturn(true);

        assertThat(service.begin(USER_ID, NOW)).isEmpty();
        verify(cleanupRepository, never()).save(any());
    }

    @Test
    @DisplayName("보존 기간이 아직 지나지 않았으면 만들지 않는다")
    void beginSkipsBeforeRetentionEnds() {
        when(user.lastContainerEndedAt()).thenReturn(Optional.of(NOW.minusDays(40).plusMinutes(1)));

        assertThat(service.begin(USER_ID, NOW)).isEmpty();
        verify(cleanupRepository, never()).save(any());
    }

    @Test
    @DisplayName("컨테이너를 쓴 적이 없으면 만들지 않는다")
    void beginSkipsUserWhoNeverHadContainer() {
        when(user.lastContainerEndedAt()).thenReturn(Optional.empty());

        assertThat(service.begin(USER_ID, NOW)).isEmpty();
    }

    @Test
    @DisplayName("같은 종료 시각으로 진행 중이거나 끝난 시도가 있으면 다시 만들지 않는다")
    void beginSkipsWhenAlreadyHandled() {
        when(cleanupRepository.existsByUser_UserIdAndStatusInAndLastContainerEndedAtGreaterThanEqual(
                eq(USER_ID), anyCollection(), eq(ENDED_LONG_AGO))).thenReturn(true);

        assertThat(service.begin(USER_ID, NOW)).isEmpty();
        verify(cleanupRepository, never()).save(any());
    }

    @Test
    @DisplayName("성공을 반영하면 DELETED가 되고, 이미 끝난 시도에는 다시 반영하지 않는다")
    void completeSettlesOnce() {
        HomeCleanup cleanup = HomeCleanup.start(user, ENDED_LONG_AGO);
        when(cleanupRepository.findByIdForUpdate(CLEANUP_ID)).thenReturn(Optional.of(cleanup));

        assertThat(service.complete(CLEANUP_ID)).isPresent();
        assertThat(cleanup.getStatus()).isEqualTo(HomeCleanupStatus.DELETED);
        assertThat(service.complete(CLEANUP_ID)).isEmpty();
        assertThat(service.fail(CLEANUP_ID, "HOME_IN_USE")).isEmpty();
        assertThat(cleanup.getStatus()).isEqualTo(HomeCleanupStatus.DELETED);
    }

    @Test
    @DisplayName("실패를 반영하면 FAILED가 되고 실패 코드를 남긴다")
    void failRecordsCode() {
        HomeCleanup cleanup = HomeCleanup.start(user, ENDED_LONG_AGO);
        when(cleanupRepository.findByIdForUpdate(CLEANUP_ID)).thenReturn(Optional.of(cleanup));

        assertThat(service.fail(CLEANUP_ID, "HOME_IN_USE")).isPresent();
        assertThat(cleanup.getStatus()).isEqualTo(HomeCleanupStatus.FAILED);
        assertThat(cleanup.getFailureCode()).isEqualTo("HOME_IN_USE");
    }

    @Test
    @DisplayName("끝난 시도에는 작업 번호를 적지 않는다")
    void recordJobOnlyWhileProcessing() {
        HomeCleanup cleanup = mock(HomeCleanup.class);
        when(cleanup.isProcessing()).thenReturn(false);
        when(cleanupRepository.findByIdForUpdate(CLEANUP_ID)).thenReturn(Optional.of(cleanup));

        service.recordJob(CLEANUP_ID, 900L);

        verify(cleanup, never()).registered(any());
    }
}
