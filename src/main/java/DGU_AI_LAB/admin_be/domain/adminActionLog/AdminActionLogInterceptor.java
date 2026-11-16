package DGU_AI_LAB.admin_be.domain.adminActionLog;

import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminActionLogInterceptor implements HandlerInterceptor {

    private final AdminActionLogWriter adminActionLogWriter;

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return;
        }

        AdminAction action = handlerMethod.getMethodAnnotation(AdminAction.class);
        if (action == null) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
            return;
        }

        Long adminId = principal.getUserId();

        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(variables instanceof Map<?, ?> pathVariables)
                || !(pathVariables.get(action.targetId()) instanceof String targetId)) {
            log.error("Missing admin action target: adminId={} action={} variable={}",
                    adminId, action.value(), action.targetId());
            return;
        }

        Object roleAttribute = request.getAttribute(AdminActionLogBodyAdvice.REQUESTED_ROLE_ATTRIBUTE);
        String requestedRole = roleAttribute instanceof String role ? role : null;

        AdminActionLog entry = new AdminActionLog(
                adminId, action.value(), targetId, requestedRole,
                response.getStatus(), LocalDateTime.now(ZoneOffset.UTC));

        adminActionLogWriter.submit(entry);
    }
}