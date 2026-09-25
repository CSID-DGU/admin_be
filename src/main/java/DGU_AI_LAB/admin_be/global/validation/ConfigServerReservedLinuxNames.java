package DGU_AI_LAB.admin_be.global.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * config-server의 예약 이름 목록을 주기적으로 받아 두고 그 목록으로 답한다.
 *
 * <p>가입 요청마다 config-server를 부르지 않도록 목록을 미리 받아 둔다. 받아 오지 못하면 마지막으로 받은
 * 목록을 계속 쓰고, 한 번도 받지 못했으면 아무 이름도 막지 않는다 — config-server가 계정을 만들 때
 * 같은 목록으로 다시 거르므로, 장애 동안 가입을 전부 막는 것보다 승인 단계에서 거절되는 쪽이 낫다.
 */
@Slf4j
@Component
public class ConfigServerReservedLinuxNames implements ReservedLinuxNames {

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private volatile Set<String> names = Set.of();

    public ConfigServerReservedLinuxNames(@Qualifier("configWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public boolean contains(String name) {
        return name != null && names.contains(name);
    }

    @Scheduled(fixedDelayString = "${config.reserved-names-refresh-ms:600000}")
    public void refresh() {
        try {
            ReservedNamesResponse response = webClient.get()
                    .uri("/reserved-names")
                    .retrieve()
                    .bodyToMono(ReservedNamesResponse.class)
                    .block(FETCH_TIMEOUT);
            if (response == null || response.names() == null || response.names().isEmpty()) {
                log.warn("[ReservedLinuxNames] config-server가 빈 예약 이름 목록을 돌려줬습니다 — 기존 목록({}개) 유지", names.size());
                return;
            }
            names = Set.copyOf(response.names().stream().filter(Objects::nonNull).toList());
            log.debug("[ReservedLinuxNames] 예약 이름 {}개 갱신", names.size());
        } catch (Exception e) {
            log.warn("[ReservedLinuxNames] 예약 이름 목록 조회 실패 — 기존 목록({}개) 유지: {}", names.size(), e.toString());
        }
    }

    record ReservedNamesResponse(List<String> names) {
    }
}
