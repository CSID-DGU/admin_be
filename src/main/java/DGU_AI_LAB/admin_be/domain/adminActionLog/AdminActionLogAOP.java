package DGU_AI_LAB.admin_be.domain.adminActionLog;

import DGU_AI_LAB.admin_be.domain.adminActionLog.service.AdminActionLogService;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AdminActionLogAOP {

    private static final String MASK = "*****";

    private final AdminActionLogService adminActionLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(adminAction)")
    public Object record(ProceedingJoinPoint joinPoint, AdminAction adminAction) throws Throwable {
        Long adminId = getAdminId();
        String targetId = getTargetId(adminAction.targetId());
        String changedValue = getRequestBody(joinPoint);

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            save(adminId, adminAction, targetId, "FAILURE", changedValue);
            throw e;
        }

        save(adminId, adminAction, targetId, resultOf(result), changedValue);
        return result;
    }
    //인증정보를 토대로 관리자 id 획득
    private Long getAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String getTargetId(String pathVariableName) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        Object saved = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (saved == null) {
            return null;
        }
        Map<String, String> pathVariables = (Map<String, String>) saved;

        return pathVariables.get(pathVariableName);
    }

    @SuppressWarnings("unchecked")
    private String getRequestBody(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Annotation[][] parameterAnnotations = signature.getMethod().getParameterAnnotations();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < args.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof RequestBody && args[i] != null) {
                    try {
                        Map<String, Object> body = objectMapper.convertValue(args[i], LinkedHashMap.class);
                        return objectMapper.writeValueAsString(mask(body));
                    } catch (Exception e) {
                        log.warn("[AdminActionLog] 요청 본문 변환 실패 - {}", e.getMessage());
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private Map<String, Object> mask(Map<String, Object> body) {
        body.replaceAll((key, value) -> key.toLowerCase().contains("password") ? MASK : value);
        return body;
    }

    private String resultOf(Object result) {
        if (result instanceof ResponseEntity<?> response && response.getStatusCode() == HttpStatus.ACCEPTED) {
            return "ACCEPTED";
        }
        return "SUCCESS";
    }

    private void save(Long adminId, AdminAction adminAction, String targetId, String result, String changedValue) {
        try {
            adminActionLogService.logRecord(adminId, adminAction.value(), targetId, result, changedValue);
        } catch (Exception e) {
            log.error("[AdminActionLog] 저장 실패 - adminId={}, action={}, targetType={}, targetId={}, result={}, changedValue={}",
                    adminId, adminAction.value(), adminAction.value().targetType(), targetId, result, changedValue, e);
        }
    }
}