package DGU_AI_LAB.admin_be.domain.adminActionLog.web;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminAction;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class AdminActionBodyAdvice extends RequestBodyAdviceAdapter {
    private final HttpServletRequest request;

    @Override
    public boolean supports(
            MethodParameter parameter,
            Type type,
            Class<? extends HttpMessageConverter<?>> converter) {
        return parameter.hasMethodAnnotation(AdminAction.class);
    }

    @Override
    public Object afterBodyRead(
            Object body,
            HttpInputMessage input,
            MethodParameter parameter,
            Type type,
            Class<? extends HttpMessageConverter<?>> converter) {
        var context = (AdminActionContext) request.getAttribute(AdminActionContext.ATTRIBUTE);
        if (context == null) return body;
        try {
            if (context.action.value()
                    == DGU_AI_LAB
                            .admin_be
                            .domain
                            .adminActionLog
                            .AdminActionType
                            .USER_PASSWORD_RESET) {
                context.captureStatus = "OMITTED_BY_POLICY";
                context.values.put("credentialsOmitted", true);
                return body;
            }
            List<String> fields =
                    switch (context.action.value()) {
                        case USER_ROLE_CHANGE -> List.of("role");
                        case USER_ACTIVATION_CHANGE -> List.of("active");
                        case REQUEST_APPROVE ->
                                List.of("imageId", "resourceGroupId", "professorApprovalConfirmed");
                        case REQUEST_MIGRATE -> List.of("nodes", "minImprovementRatio", "force");
                        case REQUEST_RESTART -> List.of("imageId", "keepChanges");
                        default -> List.of();
                    };
            if (fields.isEmpty()) {
                context.captureStatus = "OMITTED_BY_POLICY";
                return body;
            }
            // DTO 전체를 직렬화하지 않고 허용된 record accessor만 읽는다.
            for (String field : fields) {
                Object value = body.getClass().getMethod(field).invoke(body);
                if (field.equals("role")) {
                    context.requestedRole = value == null ? null : ((Enum<?>) value).name();
                    continue;
                }
                if (value instanceof List<?> list) {
                    List<String> safe = new ArrayList<>();
                    boolean truncated = list.size() > 50;
                    for (Object item : list.subList(0, Math.min(50, list.size()))) {
                        String text = item == null ? null : item.toString();
                        if (text != null && text.length() > 255) {
                            truncated = true;
                            text = text.substring(0, 255);
                        }
                        safe.add(text);
                    }
                    context.values.put(field, safe);
                    if (truncated) context.values.put("truncated", true);
                } else context.values.put(field, value);
            }
            context.captureStatus = "CAPTURED";
        } catch (Exception ex) {
            context.captureStatus = "UNAVAILABLE";
            log.error(
                    "Admin audit body capture failed: eventId={}, reason={}",
                    context.eventId,
                    ex.getClass().getSimpleName());
        }
        return body;
    }

    @Override
    public Object handleEmptyBody(
            Object body,
            HttpInputMessage input,
            MethodParameter parameter,
            Type type,
            Class<? extends HttpMessageConverter<?>> converter) {
        var context = (AdminActionContext) request.getAttribute(AdminActionContext.ATTRIBUTE);
        if (context != null) context.captureStatus = "ABSENT";
        return body;
    }
}
