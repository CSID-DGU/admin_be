package DGU_AI_LAB.admin_be.domain.adminActionLog.support;

import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 실패 응답({@link ErrorResponse})의 code 를 기록용으로 옮겨 둔다. 응답 본문은 바꾸지 않는다.
 * GlobalExceptionHandler 가 만든 응답도 이 단계를 지난다. 기록 대상 요청에서만 남긴다.
 */
@ControllerAdvice
public class AdminActionErrorCodeAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ErrorResponse error && request instanceof ServletServerHttpRequest servletRequest) {
            var raw = servletRequest.getServletRequest();
            if (raw.getAttribute(AdminActionAttributes.STARTED_AT) != null) {
                raw.setAttribute(AdminActionAttributes.ERROR_CODE, error.getCode());
            }
        }
        return body;
    }
}
