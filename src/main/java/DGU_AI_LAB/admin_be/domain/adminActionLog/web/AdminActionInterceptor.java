package DGU_AI_LAB.admin_be.domain.adminActionLog.web;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminAction;
import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.AdminActionEvent;
import DGU_AI_LAB.admin_be.domain.adminActionLog.service.AdminActionLogDispatcher;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Arrays;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class AdminActionInterceptor implements HandlerInterceptor {
    private final AdminActionLogDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) return true;
        AdminAction action = method.getMethodAnnotation(AdminAction.class);
        if (action == null || request.getAttribute(AdminActionContext.ATTRIBUTE) != null)
            return true;
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            Long adminId =
                    authentication != null
                                    && authentication.getPrincipal()
                                            instanceof CustomUserDetails principal
                            ? principal.getUserId()
                            : null;
            @SuppressWarnings("unchecked")
            Map<String, String> paths =
                    (Map<String, String>)
                            request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            String target = paths == null ? null : paths.get(action.targetId());
            boolean bodyExpected =
                    Arrays.stream(method.getMethodParameters())
                            .anyMatch(p -> p.hasParameterAnnotation(RequestBody.class));
            AdminActionContext context =
                    new AdminActionContext(
                            action,
                            adminId,
                            target != null && target.length() <= 255 ? target : null,
                            bodyExpected ? "UNAVAILABLE" : "ABSENT");
            if (target != null && target.length() > 255)
                context.values.put("targetIdOmitted", true);
            if (paths != null) {
                String extra =
                        switch (action.value()) {
                            case USER_GROUP_REMOVE -> "groupId";
                            case WARNING_CANCEL -> "warningId";
                            default -> null;
                        };
                if (extra != null && paths.get(extra) != null) {
                    if (paths.get(extra).length() <= 255)
                        context.values.put(extra, paths.get(extra));
                    else context.values.put(extra + "Omitted", true);
                }
            }
            request.setAttribute(AdminActionContext.ATTRIBUTE, context);
        } catch (RuntimeException ex) {
            log.error(
                    "Admin audit capture initialization failed: action={}, reason={}",
                    action.value(),
                    ex.getClass().getSimpleName());
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        var context = (AdminActionContext) request.getAttribute(AdminActionContext.ATTRIBUTE);
        if (context == null || context.submitted) return;
        context.submitted = true;
        try {
            // 미처리 예외가 있으면 기본 200을 성공으로 오기록하지 않는다. 0은 최종 상태 미확정이다.
            int status = ex != null && response.getStatus() < 400 ? 0 : response.getStatus();
            String values = objectMapper.writeValueAsString(context.values);
            if (values.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16384) {
                values = "{\"truncated\":true}";
                context.captureStatus = "TRUNCATED";
            }
            dispatcher.submit(
                    new AdminActionEvent(
                            context.eventId,
                            context.occurredAt,
                            context.adminId,
                            context.action.value(),
                            context.action.value().targetType(),
                            context.targetId,
                            status,
                            context.requestedRole,
                            context.captureStatus,
                            values));
        } catch (Exception failure) {
            log.error(
                    "Admin audit submission failed: eventId={}, reason={}",
                    context.eventId,
                    failure.getClass().getSimpleName());
        }
    }
}
