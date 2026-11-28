package DGU_AI_LAB.admin_be.global.config;

import DGU_AI_LAB.admin_be.domain.issuanceSheet.GoogleSheetsTabWriter;
import DGU_AI_LAB.admin_be.domain.issuanceSheet.IssuanceSheetSync;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/** 전체 component scan에서도 SHADOW가 외부 쓰기 구성 없이 기동하는지 검증한다. */
@SpringBootTest(properties = "provi.runtime-mode=SHADOW")
@DisplayName("SHADOW full application startup")
class ShadowRuntimeStartupTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void startsWithoutSchedulersOrGoogleSheetsWriters() {
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
        assertThat(context.getBeansOfType(IssuanceSheetSync.class)).isEmpty();
        assertThat(context.getBeansOfType(GoogleSheetsTabWriter.class)).isEmpty();
        assertThat(context.getBean(RuntimeModeProperties.class).shadow()).isTrue();
    }
}
