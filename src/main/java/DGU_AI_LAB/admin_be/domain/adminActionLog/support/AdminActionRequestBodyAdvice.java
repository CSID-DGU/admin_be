package DGU_AI_LAB.admin_be.domain.adminActionLog.support;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminAction;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

/**
 * {@code @AdminAction} 메서드의 본문을 객체로 읽은 직후, {@code @Valid} 검사 전에 잡아 둔다.
 * 그래서 검증에 실패한 요청(400)도 "무엇을 하려 했는지"가 남는다. 본문 스트림을 다시 읽지 않는다.
 */
@ControllerAdvice
public class AdminActionRequestBodyAdvice extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return methodParameter.hasMethodAnnotation(AdminAction.class);
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(AdminActionAttributes.BODY, body, RequestAttributes.SCOPE_REQUEST);
        }
        return body;
    }
}
