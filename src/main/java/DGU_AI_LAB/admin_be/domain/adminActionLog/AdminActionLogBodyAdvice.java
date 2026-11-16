package DGU_AI_LAB.admin_be.domain.adminActionLog;

import DGU_AI_LAB.admin_be.domain.users.dto.request.ChangeRoleRequestDTO;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

@ControllerAdvice
public class AdminActionLogBodyAdvice extends RequestBodyAdviceAdapter {

    static final String REQUESTED_ROLE_ATTRIBUTE =
            AdminActionLogBodyAdvice.class.getName() + ".requestedRole";

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        AdminAction action = parameter.getMethodAnnotation(AdminAction.class);
        return action != null
                && action.value() == AdminActionType.USER_ROLE_CHANGE
                && parameter.getParameterType() == ChangeRoleRequestDTO.class;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage,
                                MethodParameter parameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null && body instanceof ChangeRoleRequestDTO dto && dto.role() != null) {
            attributes.setAttribute(
                    REQUESTED_ROLE_ATTRIBUTE, dto.role().name(), RequestAttributes.SCOPE_REQUEST);
        }
        return body;
    }
}