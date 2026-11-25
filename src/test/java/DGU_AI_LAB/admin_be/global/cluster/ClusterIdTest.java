package DGU_AI_LAB.admin_be.global.cluster;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ClusterId")
class ClusterIdTest {

    @Test
    @DisplayName("ResourceGroup의 serverName을 공백 제거·대문자 표기로 정규화한다")
    void canonicalizesServerName() {
        assertThat(ClusterId.canonical("  farm ")).isEqualTo("FARM");
    }

    @Test
    @DisplayName("클러스터가 빠지면 기본값을 추측하지 않고 거절한다")
    void rejectsMissingCluster() {
        assertThatThrownBy(() -> ClusterId.canonical(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClusterId.canonical("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FARM과 LAB 이외의 값은 설정에 있더라도 사용하지 않는다")
    void rejectsUnsupportedCluster() {
        assertThatThrownBy(() -> ClusterId.canonical("FARM-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FARM-01");
    }
}
