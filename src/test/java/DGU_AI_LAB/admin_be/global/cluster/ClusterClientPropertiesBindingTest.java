package DGU_AI_LAB.admin_be.global.cluster;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClusterClientProperties 설정 바인딩")
class ClusterClientPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues(
                    "provi.clusters.FARM.base-url=http://farm-config",
                    "provi.clusters.FARM.api-token=farm-token",
                    "provi.clusters.FARM.timeout-seconds=1",
                    "provi.clusters.FARM.pod-timeout-seconds=2",
                    "provi.clusters.FARM.in-cluster=false",
                    "provi.clusters.FARM.kubeconfig-path=/config/farm.yaml",
                    "provi.clusters.FARM.pod-namespace=farm",
                    "provi.clusters.LAB.base-url=http://lab-config",
                    "provi.clusters.LAB.api-token=lab-token",
                    "provi.clusters.LAB.timeout-seconds=1",
                    "provi.clusters.LAB.pod-timeout-seconds=2",
                    "provi.clusters.LAB.in-cluster=false",
                    "provi.clusters.LAB.kubeconfig-path=/config/lab.yaml",
                    "provi.clusters.LAB.pod-namespace=lab"
            );

    @Test
    @DisplayName("lab-enabled를 생략하면 기본 false로 바인딩되어 FARM만 활성화한다")
    void omittedLabEnabledDefaultsToFalse() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ClusterClientProperties properties = context.getBean(ClusterClientProperties.class);
            assertThat(properties.labEnabled()).isFalse();
            assertThat(properties.clusters()).containsKeys("FARM", "LAB");
            assertThat(properties.enabledClusters()).containsOnlyKeys("FARM");
        });
    }

    @Test
    @DisplayName("lab-enabled가 true일 때만 LAB profile을 활성화한다")
    void explicitTrueEnablesLabProfile() {
        contextRunner
                .withPropertyValues("provi.lab-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ClusterClientProperties properties = context.getBean(ClusterClientProperties.class);
                    assertThat(properties.labEnabled()).isTrue();
                    assertThat(properties.enabledClusters()).containsOnlyKeys("FARM", "LAB");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ClusterClientProperties.class)
    static class PropertiesConfiguration {
    }
}
