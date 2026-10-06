package DGU_AI_LAB.admin_be.domain.home.service;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HomeRetentionNotice")
class HomeRetentionNoticeTest {

    private static final Long USER_ID = 7L;
    private static final Long REQUEST_ID = 41L;
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 11, 10, 15, 0);

    @Mock private RequestRepository requestRepository;
    @Mock private MessageUtils messageUtils;

    private HomeRetentionNotice notice;
    private Request request;

    @BeforeEach
    void setUp() {
        notice = new HomeRetentionNotice(requestRepository, new HomeRetentionPolicy(), messageUtils);
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(USER_ID);
        request = mock(Request.class);
        when(request.getUser()).thenReturn(user);
        when(request.getRequestId()).thenReturn(REQUEST_ID);
        when(request.getUbuntuUsername()).thenReturn("hong");
        when(request.getExpiresAt()).thenReturn(EXPIRES_AT);
        when(messageUtils.get("notification.home.kept", "hong")).thenReturn("유지");
    }

    /** 그 날짜 다음 날 0시 이후까지 이어지는 다른 컨테이너가 있는지에 대한 답을 정한다. */
    private void otherContainerOutlives(LocalDate endDate, boolean outlives) {
        when(requestRepository.existsByUser_UserIdAndRequestIdNotAndStatusInAndExpiresAtGreaterThanEqual(
                USER_ID, REQUEST_ID, Status.activeStatuses(), endDate.plusDays(1).atStartOfDay()))
                .thenReturn(outlives);
    }

    @Test
    @DisplayName("만료 예고: 마지막 컨테이너면 종료 예정일에서 30일 뒤를 삭제일로 알린다")
    void beforeExpiryLastContainerAnnouncesDeletionDate() {
        otherContainerOutlives(EXPIRES_AT.toLocalDate(), false);
        when(messageUtils.get("notification.home.before-expiry.last", "hong", "2026-12-10", "30")).thenReturn("삭제 예정");

        assertThat(notice.beforeExpiry(request)).isEqualTo("삭제 예정");
    }

    @Test
    @DisplayName("만료 예고: 종료 예정일 다음 날 이후까지 남는 다른 컨테이너가 있으면 유지된다고 알린다")
    void beforeExpiryWithLongerContainerSaysKept() {
        otherContainerOutlives(EXPIRES_AT.toLocalDate(), true);

        assertThat(notice.beforeExpiry(request)).isEqualTo("유지");
    }

    @Test
    @DisplayName("종료 뒤: 마지막 컨테이너면 실제로 끝난 날에서 30일 뒤를 삭제일로 알린다")
    void afterEndLastContainerCountsFromActualEndDate() {
        LocalDate endedOn = LocalDate.of(2026, 10, 6);
        otherContainerOutlives(endedOn, false);
        when(messageUtils.get("notification.home.after-end.last", "hong", "2026-11-05", "30")).thenReturn("삭제 예정");

        assertThat(notice.afterEnd(request, endedOn)).isEqualTo("삭제 예정");
    }

    @Test
    @DisplayName("종료 뒤: 끝난 날 다음 날 이후까지 남는 다른 컨테이너가 있으면 유지된다고 알린다")
    void afterEndWithOtherContainerSaysKept() {
        LocalDate endedOn = LocalDate.of(2026, 10, 6);
        otherContainerOutlives(endedOn, true);

        assertThat(notice.afterEnd(request, endedOn)).isEqualTo("유지");
    }
}
