package DGU_AI_LAB.admin_be.domain.home.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HomeRetentionPolicyTest {

    private static HomeRetentionPolicy policy(int announcedDays, int deleteDays) {
        HomeRetentionPolicy policy = new HomeRetentionPolicy();
        ReflectionTestUtils.setField(policy, "announcedDays", announcedDays);
        ReflectionTestUtils.setField(policy, "deleteDays", deleteDays);
        return policy;
    }

    @Test
    @DisplayName("기본값과, 지우는 날이 알린 날과 같거나 늦은 설정은 받는다")
    void acceptsDeleteOnOrAfterAnnounced() {
        assertThatCode(() -> new HomeRetentionPolicy().validate()).doesNotThrowAnyException();
        assertThatCode(() -> policy(30, 30).validate()).doesNotThrowAnyException();
        assertThatCode(() -> policy(0, 1).validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("알린 날보다 먼저 지우는 설정이면 기동하지 않는다")
    void rejectsDeleteBeforeAnnounced() {
        assertThatThrownBy(() -> policy(30, 1).validate()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy(-1, 10).validate()).isInstanceOf(IllegalStateException.class);
    }
}
