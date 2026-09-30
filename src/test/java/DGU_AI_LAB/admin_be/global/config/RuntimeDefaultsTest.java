package DGU_AI_LAB.admin_be.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RuntimeDefaults")
class RuntimeDefaultsTest {

    @Configuration
    @EnableScheduling
    @ImportAutoConfiguration({MailSenderAutoConfiguration.class, TaskSchedulingAutoConfiguration.class})
    static class MailAndScheduling {
    }

    private static ConfigurableApplicationContext run(String... args) {
        SpringApplication app = new SpringApplication(MailAndScheduling.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(RuntimeDefaults.values());
        return app.run(args);
    }

    @Test
    @DisplayName("설정 파일에 없으면 SMTP 시간 제한과 스케줄러 스레드 수를 기본값으로 쓴다")
    void appliesDefaults() {
        try (var ctx = run("--spring.mail.host=localhost", "--spring.config.location=optional:classpath:/none.yml")) {
            var props = ctx.getBean(JavaMailSenderImpl.class).getJavaMailProperties();
            assertThat(props.getProperty("mail.smtp.connectiontimeout")).isEqualTo("5000");
            assertThat(props.getProperty("mail.smtp.timeout")).isEqualTo("10000");
            assertThat(props.getProperty("mail.smtp.writetimeout")).isEqualTo("10000");
            assertThat(ctx.getBean(ThreadPoolTaskScheduler.class).getScheduledThreadPoolExecutor().getCorePoolSize())
                    .isEqualTo(10);
        }
    }

    @Test
    @DisplayName("설정 파일의 메일 속성과 키 단위로 합쳐지고, 같은 키는 설정 값이 이긴다")
    void configuredValuesWinPerKey() {
        try (var ctx = run("--spring.mail.host=localhost", "--spring.config.location=optional:classpath:/none.yml",
                "--spring.mail.properties.mail.smtp.auth=true",
                "--spring.mail.properties.mail.smtp.timeout=2000",
                "--spring.task.scheduling.pool.size=2")) {
            var props = ctx.getBean(JavaMailSenderImpl.class).getJavaMailProperties();
            assertThat(props.getProperty("mail.smtp.auth")).isEqualTo("true");
            assertThat(props.getProperty("mail.smtp.timeout")).isEqualTo("2000");
            assertThat(props.getProperty("mail.smtp.connectiontimeout")).isEqualTo("5000");
            assertThat(ctx.getBean(ThreadPoolTaskScheduler.class).getScheduledThreadPoolExecutor().getCorePoolSize())
                    .isEqualTo(2);
        }
    }

    @Test
    @DisplayName("API 문서와 Swagger 화면은 설정 파일에서 켜지 않으면 꺼져 있다")
    void apiDocsDisabledByDefault() {
        assertThat(RuntimeDefaults.values())
                .containsEntry("springdoc.api-docs.enabled", "false")
                .containsEntry("springdoc.swagger-ui.enabled", "false");
    }
}
