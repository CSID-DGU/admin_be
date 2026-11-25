package DGU_AI_LAB.admin_be.global.cluster;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
        @NotEmpty Map<String, @Valid Cluster> clusters
) {

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
