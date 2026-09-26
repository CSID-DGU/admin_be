package DGU_AI_LAB.admin_be.global.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 스키마 변경은 Flyway 버전 파일(src/main/resources/db/migration)로만 한다.
 *
 * <p>V1__baseline.sql은 Flyway 도입 시점에 모든 스택의 스키마를 맞춘 뒤 뜬 기준이다. 표가 이미 있고 이력 표가 없는
 * DB(도입 전부터 있던 스택)는 V1을 실행하지 않고 적용된 것으로만 기록하고, 빈 DB(새 스택)에서만 V1을 실행한다.
 * 이 동작은 배포 환경마다 다를 이유가 없어 설정 파일이 아니라 코드에 둔다.
 */
@Configuration
public class FlywayConfig {

    static final String BASELINE_VERSION = "1";

    @Bean
    public FlywayConfigurationCustomizer baselineExistingSchema() {
        return configuration -> configuration
                .baselineOnMigrate(true)
                .baselineVersion(BASELINE_VERSION);
    }
}
