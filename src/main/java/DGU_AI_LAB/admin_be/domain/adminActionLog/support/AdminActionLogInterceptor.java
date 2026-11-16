package DGU_AI_LAB.admin_be.domain.adminActionLog.support;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminAction;
import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import DGU_AI_LAB.admin_be.domain.adminActionLog.service.AdminActionLogWriter;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * {@code @AdminAction}이 붙은 관리자 API 의 요청마다 기록을 한 줄 남긴다.
 *
 * <ul>
 *   <li>preHandle: 불릴 메서드가 정해진 뒤라 어노테이션을 읽는다. 기록 대상이면 요청 시각을 표시해 둔다.</li>
 *   <li>afterCompletion: 검증(400)·업무 규칙(4xx)·서버 오류(5xx)까지 모두 응답으로 바뀐 뒤라 최종 상태 코드를 본다.
 *       예외를 GlobalExceptionHandler 가 처리하면 {@code ex}는 null 이므로 결과는 상태 코드로만 판단한다.</li>
 * </ul>
 *
 * 저장이 실패해도 관리자 작업의 응답은 바꾸지 않는다. 대신 가린 기록 내용을 {@value #LOG_TAG} ERROR 로그로 남겨
 * 로그에서 되살릴 수 있게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminActionLogInterceptor implements HandlerInterceptor {

    static final String LOG_TAG = "[ADMIN_ACTION_LOG]";
    private static final int URI_MAX = 500;

    // @WebMvcTest 조각 테스트는 인터셉터만 올리고 저장·가리기 빈은 올리지 않는다. 직접 주입받으면
    // 기존 컨트롤러 테스트의 컨텍스트가 뜨지 않으므로 ObjectProvider 로 받는다. 실제 실행에서는 둘 다 항상 있다.
    private final ObjectProvider<AdminActionLogWriter> writerProvider;
    private final ObjectProvider<AdminActionMasker> maskerProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (annotationOf(handler) != null) {
            request.setAttribute(AdminActionAttributes.STARTED_AT, LocalDateTime.now());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        AdminAction action = annotationOf(handler);
        if (action == null || request.getAttribute(AdminActionAttributes.STARTED_AT) == null) {
            return;
        }
        AdminActionLogWriter writer = writerProvider.getIfAvailable();
        AdminActionMasker masker = maskerProvider.getIfAvailable();
        if (writer == null || masker == null) {
            return;
        }
        AdminActionLog entry;
        try {
            entry = build(action, request, response, ex, masker);
        } catch (Exception e) {
            log.error("{} 기록을 만들지 못함 action={} uri={}", LOG_TAG, action.value(), request.getRequestURI(), e);
            return;
        }
        try {
            writer.write(entry);
        } catch (Exception e) {
            log.error("{} 저장 실패 action={} targetType={} targetId={} adminId={} status={} errorCode={} "
                            + "method={} uri={} createdAt={} pathVariables={} queryParams={} body={}",
                    LOG_TAG, entry.getAction(), entry.getTargetType(), entry.getTargetId(), entry.getAdminId(),
                    entry.getHttpStatus(), entry.getErrorCode(), entry.getHttpMethod(), entry.getRequestUri(),
                    entry.getCreatedAt(), entry.getPathVariables(), entry.getQueryParams(),
                    entry.getRequestBody(), e);
        }
    }

    private AdminActionLog build(AdminAction action, HttpServletRequest request, HttpServletResponse response,
                                 Exception ex, AdminActionMasker masker) {
        Map<String, String> pathVariables = pathVariables(request);
        CustomUserDetails admin = currentAdmin();
        // 처리되지 않은 예외가 그대로 나갔다면 상태 코드가 아직 200 일 수 있어 서버 오류로 본다.
        int status = (ex != null && response.getStatus() < 400) ? 500 : response.getStatus();

        return AdminActionLog.builder()
                .createdAt((LocalDateTime) request.getAttribute(AdminActionAttributes.STARTED_AT))
                .action(action.value().name())
                .targetType(action.value().targetType().name())
                .targetId(pathVariables.get(action.targetId()))
                .adminId(admin != null ? admin.getUserId() : null)
                .adminName(admin != null ? admin.getName() : null)
                .httpMethod(request.getMethod())
                .requestUri(truncate(request.getRequestURI(), URI_MAX))
                .httpStatus(status)
                .errorCode((String) request.getAttribute(AdminActionAttributes.ERROR_CODE))
                .pathVariables(pathVariables.isEmpty() ? null : masker.write(pathVariables))
                .queryParams(masker.maskParams(request.getParameterMap()))
                .requestBody(masker.maskBody(request.getAttribute(AdminActionAttributes.BODY)))
                .build();
    }

    private static AdminAction annotationOf(Object handler) {
        return handler instanceof HandlerMethod method ? method.getMethodAnnotation(AdminAction.class) : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> pathVariables(HttpServletRequest request) {
        Object value = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return value instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }

    private static CustomUserDetails currentAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details
                ? details : null;
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}
