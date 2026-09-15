package DGU_AI_LAB.admin_be.global.config;

import io.netty.channel.ChannelOption;
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

    /** config-server가 admin_be 호출만 받도록 확인하는 공유 토큰 헤더. */
    static final String API_TOKEN_HEADER = "X-Internal-Token";

    /** 토큰이 설정돼 있으면 모든 요청에 붙인다. 비어 있으면(로컬 개발) 붙이지 않는다. */
    static WebClient configServerClient(String baseUrl, HttpClient httpClient, String apiToken) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient));
        if (apiToken != null && !apiToken.isBlank()) {
            builder.defaultHeader(API_TOKEN_HEADER, apiToken);
        }
        return builder.build();
    }

    @Bean
    public WebClient configWebClient(@Value("${config.base-url}") String baseUrl,
                                     @Value("${config.timeout-seconds}") int timeout,
                                     @Value("${config.api-token:}") String apiToken) {

        ConnectionProvider provider = ConnectionProvider.builder("pvc-connection-pool")
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(20))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(timeout));

        return configServerClient(baseUrl, httpClient, apiToken);
    }

    @Bean
    public WebClient podWebClient(@Value("${config.base-url}") String baseUrl,
                                  @Value("${config.pod-timeout-seconds:600}") int podTimeout,
                                  @Value("${config.api-token:}") String apiToken) {

        ConnectionProvider provider = ConnectionProvider.builder("pod-connection-pool")
                .maxConnections(10)
                .maxIdleTime(Duration.ofSeconds(60))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30_000)
                .responseTimeout(Duration.ofSeconds(podTimeout));

        return configServerClient(baseUrl, httpClient, apiToken);
    }
}