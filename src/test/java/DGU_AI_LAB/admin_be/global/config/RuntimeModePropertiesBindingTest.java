package DGU_AI_LAB.admin_be.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RuntimeModeProperties binding")
class RuntimeModePropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BindingConfiguration.class);

    @Test
    void bindsExplicitModes() {
        runner.withPropertyValues("provi.runtime-mode=SHADOW").run(context ->
                assertThat(context.getBean(RuntimeModeProperties.class).shadow()).isTrue());
        runner.withPropertyValues("provi.runtime-mode=ACTIVE").run(context ->
                assertThat(context.getBean(RuntimeModeProperties.class).shadow()).isFalse());
    }

    @Test
    void missingModeFailsClosedAtStartup() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void unknownModeFailsClosedAtStartup() {
        runner.withPropertyValues("provi.runtime-mode=UNKNOWN").run(context ->
                assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RuntimeModeProperties.class)
    static class BindingConfiguration {
    }
}
