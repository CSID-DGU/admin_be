package DGU_AI_LAB.admin_be.domain.messageTemplate.controller;

import DGU_AI_LAB.admin_be.domain.messageTemplate.service.MessageTemplateService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 메시지 템플릿 수정 요청의 본문 검증을 확인한다.
 * value가 없으면 DB not-null 제약 위반으로 500이 나가므로 컨트롤러에서 400으로 끊는다.
 */
@ExtendWith(MockitoExtension.class)
class AdminMessageTemplateControllerTest {

    private static final String KEY = "notification.expired.dm";

    @InjectMocks
    private AdminMessageTemplateController controller;

    @Mock
    private MessageTemplateService messageTemplateService;

    @Test
    @DisplayName("value가 있으면 서비스로 위임한다")
    void update_withValue_delegatesToService() {
        assertThatCode(() -> controller.update(KEY, Map.of("value", "새 메시지")))
                .doesNotThrowAnyException();

        verify(messageTemplateService).update(KEY, "새 메시지");
    }

    @Test
    @DisplayName("value 키가 없으면 INVALID_INPUT_VALUE로 거절하고 서비스를 호출하지 않는다")
    void update_missingValueKey_isRejected() {
        assertThatThrownBy(() -> controller.update(KEY, Map.of("other", "x")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(messageTemplateService, never()).update(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("value가 null이면 INVALID_INPUT_VALUE로 거절한다")
    void update_nullValue_isRejected() {
        Map<String, String> body = new HashMap<>();
        body.put("value", null);

        assertThatThrownBy(() -> controller.update(KEY, body))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("value가 공백뿐이면 INVALID_INPUT_VALUE로 거절한다")
    void update_blankValue_isRejected() {
        assertThatThrownBy(() -> controller.update(KEY, Map.of("value", "   ")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
