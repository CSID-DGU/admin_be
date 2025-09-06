package DGU_AI_LAB.admin_be.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient configWebClient(@Value("${config.base-url}") String baseUrl,
                                     @Value("${config.timeout-seconds}") int timeout) {

        /**
         * 연결 풀 설정: HTTP 연결을 효율적으로 재사용하도록 한다.
         */
        ConnectionProvider provider = ConnectionProvider.builder("pvc-connection-pool")
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(20))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .responseTimeout(Duration.ofSeconds(timeout));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }


}