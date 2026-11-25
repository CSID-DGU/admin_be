package DGU_AI_LAB.admin_be.global.cluster;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FARM·LAB처럼 서로 독립된 클러스터에 연결할 때 필요한 값.
 *
 * <p>주소·토큰·kubeconfig를 한 묶음으로 두어 한 클러스터의 주소에 다른 클러스터의 자격증명을
 * 붙이는 조합을 만들지 않는다. 기존 {@code config.*}, {@code kubernetes.*} 단일 설정은 사용하지
 * 않으며, 여기에 없는 클러스터는 {@link ClusterClientRegistry}가 거절한다.
 */
@Validated
@ConfigurationProperties(prefix = "provi")
public record ClusterClientProperties(
        @NotEmpty Map<String, @Valid Cluster> clusters,
        boolean labEnabled
) {

    /**
     * LAB profile을 미리 배치해도 feature flag를 켜기 전에는 client와 내부 token을 활성화하지 않는다.
     * FARM은 전환 중 기존 관리 경로를 유지해야 하므로 항상 활성 대상이다.
     */
    public Map<String, Cluster> enabledClusters() {
        if (clusters == null || labEnabled) {
            return clusters;
        }
        Map<String, Cluster> enabled = new LinkedHashMap<>();
        clusters.forEach((rawClusterId, settings) -> {
            if (!"LAB".equals(ClusterId.canonical(rawClusterId))) {
                enabled.put(rawClusterId, settings);
            }
        });
        return Map.copyOf(enabled);
    }

    public record Cluster(
            @NotBlank String baseUrl,
            @NotBlank String apiToken,
            @Min(1) int timeoutSeconds,
            @Min(1) int podTimeoutSeconds,
            boolean inCluster,
            String kubeconfigPath,
            @NotBlank String podNamespace
    ) {
        @AssertTrue(message = "in-cluster와 kubeconfig-path 중 정확히 하나를 설정해야 합니다.")
        public boolean isKubernetesAccessModeValid() {
            boolean hasKubeconfig = kubeconfigPath != null && !kubeconfigPath.isBlank();
            return inCluster != hasKubeconfig;
        }
    }
}
