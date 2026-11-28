package DGU_AI_LAB.admin_be.global.config;

import DGU_AI_LAB.admin_be.domain.issuanceSheet.IssuanceSheetQueryService;
import DGU_AI_LAB.admin_be.domain.issuanceSheet.IssuanceSheetSync;
import DGU_AI_LAB.admin_be.domain.issuanceSheet.GoogleSheetsTabWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Admin runtime mode")
class RuntimeModeConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    @DisplayName("SHADOW는 scheduler 인프라와 startup sync 빈을 만들지 않는다")
    void shadowHasNoSchedulingOrStartupSync() {
        contextRunner.withPropertyValues("provi.runtime-mode=SHADOW").run(context -> {
            assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(context).doesNotHaveBean(IssuanceSheetSync.class);
            assertThat(context).doesNotHaveBean(GoogleSheetsTabWriter.class);
            assertThat(context.getBean(RuntimeModeProperties.class).shadow()).isTrue();
        });
    }

    @Test
    @DisplayName("ACTIVE는 scheduler 인프라와 startup sync 빈을 만든다")
    void activeEnablesSchedulingAndStartupSync() {
        contextRunner.withPropertyValues("provi.runtime-mode=ACTIVE").run(context -> {
            assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(context).hasSingleBean(IssuanceSheetSync.class);
            assertThat(context).hasSingleBean(GoogleSheetsTabWriter.class);
            assertThat(context.getBean(RuntimeModeProperties.class).shadow()).isFalse();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RuntimeModeProperties.class)
    @Import({SchedulingConfiguration.class, IssuanceSheetSync.class, GoogleSheetsTabWriter.class})
    static class TestConfiguration {
        @Bean
        IssuanceSheetQueryService issuanceSheetQueryService() {
            return mock(IssuanceSheetQueryService.class);
        }
    }
}
