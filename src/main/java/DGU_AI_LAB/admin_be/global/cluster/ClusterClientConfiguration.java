package DGU_AI_LAB.admin_be.global.cluster;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 단일 클라이언트 Bean 대신 클러스터별 연결 registry 하나만 등록한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClusterClientProperties.class)
public class ClusterClientConfiguration {

    @Bean(destroyMethod = "close")
    public ClusterClientRegistry clusterClientRegistry(ClusterClientProperties properties) {
        return new ClusterClientRegistry(properties);
    }
}
