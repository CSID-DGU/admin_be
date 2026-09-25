package DGU_AI_LAB.admin_be.global.server;

import DGU_AI_LAB.admin_be.global.server.ServerProfileProperties.PortForwarding;
import DGU_AI_LAB.admin_be.global.server.ServerProfileProperties.Server;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerProfileRegistryTest {

    private static final String FARM_WEBHOOK = "https://hooks.slack.com/farm";

    private static ServerProfileRegistry registry(Map<String, Server> servers) {
        return new ServerProfileRegistry(new ServerProfileProperties(servers),
                new MockEnvironment().withProperty("slack-webhook-url.farm-admin", FARM_WEBHOOK));
    }

    private static final Server FARM = new Server("farm.example.org", "farm-admin",
            new PortForwarding(30000, 9300, 98));

    @Test
    @DisplayName("서버 이름은 대소문자·앞뒤 공백을 가리지 않고 찾는다")
    void lookupIsCaseInsensitive() {
        ServerProfileRegistry registry = registry(Map.of("FARM", FARM));

        assertThat(registry.publicHost(" farm ")).contains("farm.example.org");
        assertThat(registry.adminWebhookUrl("Farm")).contains(FARM_WEBHOOK);
    }

    @Test
    @DisplayName("모르는 서버·null은 빈 값을 돌려주고 포트는 그대로 둔다")
    void unknownServerYieldsEmpty() {
        ServerProfileRegistry registry = registry(Map.of("FARM", FARM));

        assertThat(registry.publicHost("LAB")).isEmpty();
        assertThat(registry.adminWebhookUrl(null)).isEmpty();
        assertThat(registry.publicPort("LAB", "30022")).isEqualTo("30022");
    }

    @Test
    @DisplayName("포워딩 대역 안의 NodePort만 같은 오프셋의 공인 포트로 바꾼다")
    void publicPortMapsOnlyInsideBand() {
        ServerProfileRegistry registry = registry(Map.of("FARM", FARM));

        assertThat(registry.publicPort("FARM", "30000")).isEqualTo("9300");
        assertThat(registry.publicPort("FARM", "30097")).isEqualTo("9397");
        assertThat(registry.publicPort("FARM", "30098")).isEqualTo("30098");
        assertThat(registry.publicPort("FARM", "29999")).isEqualTo("29999");
        assertThat(registry.publicPort("FARM", "")).isEmpty();
    }

    @Test
    @DisplayName("포워딩 구성이 없는 서버는 NodePort를 그대로 안내한다")
    void noForwardingKeepsNodePort() {
        ServerProfileRegistry registry = registry(Map.of("FARM", new Server("farm.example.org", "farm-admin", null)));

        assertThat(registry.publicPort("FARM", "30022")).isEqualTo("30022");
    }

    @Test
    @DisplayName("admin-channel이 가리키는 webhook 설정이 없으면 기동 단계에서 실패한다")
    void missingWebhookFailsFast() {
        Map<String, Server> servers = Map.of("LAB", new Server("lab.example.org", "lab-admin", null));

        assertThatThrownBy(() -> registry(servers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slack-webhook-url.lab-admin");
    }

    @Test
    @DisplayName("대소문자만 다른 서버 이름이 겹치면 기동 단계에서 실패한다")
    void duplicateNamesFailFast() {
        Map<String, Server> servers = Map.of("FARM", FARM, "farm", FARM);

        assertThatThrownBy(() -> registry(servers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("겹친다");
    }
}
