package DGU_AI_LAB.admin_be.global.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 중앙 Admin의 실행 역할. 누락되거나 알 수 없는 값은 설정 바인딩 단계에서 기동을 실패시킨다.
 */
@Validated
@ConfigurationProperties(prefix = "provi")
public record RuntimeModeProperties(@NotNull Mode runtimeMode) {

    public boolean shadow() {
        return runtimeMode == Mode.SHADOW;
    }

    public enum Mode {
        SHADOW,
        ACTIVE
    }
}
