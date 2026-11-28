package DGU_AI_LAB.admin_be.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 예약 작업 인프라는 ACTIVE에서만 만든다. SHADOW에는 scheduler post-processor 자체가 없다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "provi", name = "runtime-mode", havingValue = "ACTIVE")
public class SchedulingConfiguration {
}
