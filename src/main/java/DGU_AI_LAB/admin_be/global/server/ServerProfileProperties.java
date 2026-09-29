package DGU_AI_LAB.admin_be.global.server;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * 서버(리소스 그룹의 serverName)마다 다른 운영 정보. 주소·포트 대역·알림 채널은 배포 환경의 사실이라
 * 코드가 아니라 설정({@code app.servers.<serverName>})에 둔다. 서버가 늘면 설정 항목만 추가한다.
 *
 * <pre>
 * app:
 *   servers:
 *     FARM:
 *       public-host: 203.0.113.10
 *       admin-channel: farm-admin      # slack-webhook-url.farm-admin
 *       port-forwarding:               # 생략하면 NodePort를 그대로 안내
 *         node-port-base: 30000
 *         public-port-base: 9300
 *         size: 98
 * </pre>
 *
 * <p>admin-channel은 webhook 주소가 아니라 {@code slack-webhook-url} 아래 키 이름이다. webhook은 비밀값이라
 * 한곳에 모아 두고, 실험 스택처럼 알림을 막아야 하는 환경이 그 한곳만 덮어쓰면 되게 한다.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record ServerProfileProperties(@NotEmpty Map<String, @Valid Server> servers) {

    public record Server(@NotBlank String publicHost,
                         @NotBlank String adminChannel,
                         @Valid PortForwarding portForwarding) {
    }

    /**
     * 공인 주소의 [publicPortBase, publicPortBase + size) 대역이 NodePort [nodePortBase, nodePortBase + size)로
     * 같은 오프셋에 포워딩되는 구성.
     */
    public record PortForwarding(@Min(1) int nodePortBase, @Min(1) int publicPortBase, @Min(1) int size) {
    }
}
