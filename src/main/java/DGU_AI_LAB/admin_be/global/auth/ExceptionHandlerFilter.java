package DGU_AI_LAB.admin_be.global.auth;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.dto.ErrorResponse;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.error.exception.InvalidValueException;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class ExceptionHandlerFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException | InvalidValueException e) {
            // 필터 단계에서 던진 비즈니스 예외는 @RestControllerAdvice를 타지 못하므로
            // 여기서 각 예외가 들고 있는 ErrorCode의 상태 코드 그대로 직접 응답한다.
            handleBusinessException(response, e);
        } catch (Exception ee) {
            handleException(response);
        }
    }

    private void handleBusinessException(HttpServletResponse response, BusinessException e) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("utf-8");
        response.setStatus(e.getErrorCode().getHttpStatus().value());
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(e.getErrorCode())));
    }

    private void handleException(HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("utf-8");
        response.setStatus(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus().value());
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR)));
    }
}
