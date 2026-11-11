package DGU_AI_LAB.admin_be.domain.home.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HomeCleanupNotifier")
class HomeCleanupNotifierTest {

    @Test
    @DisplayName("확보 용량은 GB 한 자리로, 모르면 미확인으로 알린다")
    void freedText() {
        assertThat(HomeCleanupNotifier.freed(null)).isEqualTo("확보 용량 미확인");
        assertThat(HomeCleanupNotifier.freed(0L)).isEqualTo("0.0 GB 확보");
        assertThat(HomeCleanupNotifier.freed(1536L * 1024 * 1024)).isEqualTo("1.5 GB 확보");
    }
}
