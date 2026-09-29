package DGU_AI_LAB.admin_be.global.server;

import DGU_AI_LAB.admin_be.global.server.ServerProfileProperties.PortForwarding;
import DGU_AI_LAB.admin_be.global.server.ServerProfileProperties.Server;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * serverName으로 {@link ServerProfileProperties}의 서버 정보를 찾는다. 이름은 대소문자를 가리지 않는다
 * (DB의 server_name과 설정 키의 표기가 달라도 같은 서버로 본다). 모르는 서버는 빈 값으로 돌려주고,
 * 무엇으로 대신할지는 호출부가 정한다.
 */
@Component
@EnableConfigurationProperties(ServerProfileProperties.class)
public class ServerProfileRegistry {

    static final String WEBHOOK_PREFIX = "slack-webhook-url.";

    private final Map<String, Server> servers = new HashMap<>();
    private final Environment environment;

    public ServerProfileRegistry(ServerProfileProperties properties, Environment environment) {
        this.environment = environment;
        properties.servers().forEach((name, server) -> {
            if (servers.put(normalize(name), server) != null) {
                throw new IllegalStateException("app.servers에 대소문자만 다른 서버 이름이 겹친다: " + name);
            }
            if (!environment.containsProperty(WEBHOOK_PREFIX + server.adminChannel())) {
                throw new IllegalStateException("app.servers." + name + ".admin-channel이 가리키는 "
                        + WEBHOOK_PREFIX + server.adminChannel() + " 설정이 없다");
            }
        });
    }

    public Optional<String> publicHost(String serverName) {
        return find(serverName).map(Server::publicHost);
    }

    public Optional<String> adminWebhookUrl(String serverName) {
        return find(serverName).map(server -> environment.getProperty(WEBHOOK_PREFIX + server.adminChannel()));
    }

    /**
     * 사용자에게 안내할 공인 포트. 포워딩 구성이 없거나 대역 밖이거나 숫자가 아니면 NodePort를 그대로 돌려준다 —
     * 외부에 열리지 않은 포트라는 사실이 그대로 드러나는 편이 틀린 번호를 안내하는 것보다 낫다.
     */
    public String publicPort(String serverName, String nodePort) {
        return find(serverName)
                .map(Server::portForwarding)
                .map(forwarding -> toPublicPort(forwarding, nodePort))
                .orElse(nodePort);
    }

    private static String toPublicPort(PortForwarding forwarding, String nodePort) {
        try {
            int offset = Integer.parseInt(nodePort) - forwarding.nodePortBase();
            if (offset >= 0 && offset < forwarding.size()) {
                return String.valueOf(forwarding.publicPortBase() + offset);
            }
        } catch (NumberFormatException ignored) {
            // 빈 문자열 등 — 원본 그대로 반환
        }
        return nodePort;
    }

    private Optional<Server> find(String serverName) {
        if (serverName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(servers.get(normalize(serverName)));
    }

    private static String normalize(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }
}
