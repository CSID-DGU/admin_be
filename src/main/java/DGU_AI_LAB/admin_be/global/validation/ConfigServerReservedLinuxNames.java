package DGU_AI_LAB.admin_be.global.validation;

import DGU_AI_LAB.admin_be.global.cluster.ClusterClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 각 config-server의 예약 이름 목록을 주기적으로 받아 두고 합집합으로 답한다.
 *
 * <p>가입 요청마다 config-server를 부르지 않도록 목록을 미리 받아 둔다. 한 클러스터의
 * 조회가 실패해도 다른 클러스터의 갱신은 계속하고, 실패한 클러스터는 자신의 마지막 성공
 * 목록을 유지한다. 한 번도 받지 못한 클러스터는 빈 목록으로 두며, config-server가 계정을
 * 만들 때 같은 목록으로 다시 검사한다.
 */
@Slf4j
@Component
public class ConfigServerReservedLinuxNames implements ReservedLinuxNames {

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(5);

    private final ClusterClientRegistry clients;
    private volatile Map<String, Set<String>> namesByCluster = Map.of();
    private volatile Set<String> names = Set.of();

    public ConfigServerReservedLinuxNames(ClusterClientRegistry clients) {
        this.clients = clients;
    }

    @Override
    public boolean contains(String name) {
        return name != null && names.contains(name);
    }

    @Scheduled(fixedDelayString = "${config.reserved-names-refresh-ms:600000}")
    public synchronized void refresh() {
        Map<String, Set<String>> refreshed = new LinkedHashMap<>(namesByCluster);
        for (String clusterId : clients.clusterIds()) {
            try {
                ReservedNamesResponse response = clients.configWebClient(clusterId).get()
                        .uri("/reserved-names")
                        .retrieve()
                        .bodyToMono(ReservedNamesResponse.class)
                        .block(FETCH_TIMEOUT);
                if (response == null || response.names() == null || response.names().isEmpty()) {
                    log.warn("[ReservedLinuxNames] {} config-server가 빈 목록을 반환함 — 해당 클러스터의 기존 목록 유지", clusterId);
                    continue;
                }
                Set<String> clusterNames = Set.copyOf(response.names().stream()
                        .filter(Objects::nonNull)
                        .toList());
                refreshed.put(clusterId, clusterNames);
                log.debug("[ReservedLinuxNames] {} 예약 이름 {}개 갱신", clusterId, clusterNames.size());
            } catch (Exception e) {
                log.warn("[ReservedLinuxNames] {} 목록 조회 실패 — 해당 클러스터의 기존 목록 유지: {}", clusterId, e.toString());
            }
        }
        namesByCluster = Map.copyOf(refreshed);
        Set<String> allNames = new HashSet<>();
        refreshed.values().forEach(allNames::addAll);
        names = Set.copyOf(allNames);
        log.debug("[ReservedLinuxNames] 클러스터 {}개의 예약 이름 합집합 {}개 반영", refreshed.size(), names.size());
    }

    record ReservedNamesResponse(List<String> names) {
    }
}
