package DGU_AI_LAB.admin_be.global.auth;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.InvalidValueException;
import DGU_AI_LAB.admin_be.error.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ExceptionHandlerFilterTest {

    private final ExceptionHandlerFilter filter = new ExceptionHandlerFilter();

    private MockHttpServletResponse runWith(Exception thrown) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(thrown).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("UnauthorizedException은 해당 ErrorCode의 상태 코드로 응답한다")
    void routesUnauthorizedExceptionWithItsOwnStatus() throws Exception {
        MockHttpServletResponse response = runWith(new UnauthorizedException(ErrorCode.EXPIRED_ACCESS_TOKEN));

        assertThat(response.getStatus()).isEqualTo(ErrorCode.EXPIRED_ACCESS_TOKEN.getHttpStatus().value());
        assertThat(response.getContentAsString()).contains(ErrorCode.EXPIRED_ACCESS_TOKEN.getMessage());
    }

    @Test
    @DisplayName("InvalidValueException도 500이 아니라 해당 ErrorCode의 상태 코드로 응답한다")
    void routesInvalidValueExceptionWithItsOwnStatus() throws Exception {
        MockHttpServletResponse response = runWith(new InvalidValueException(ErrorCode.INVALID_INPUT_VALUE));

        assertThat(response.getStatus()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus().value());
        assertThat(response.getContentAsString()).contains(ErrorCode.INVALID_INPUT_VALUE.getMessage());
    }

    @Test
    @DisplayName("그 외 예외는 500으로 응답한다")
    void fallsBackToInternalServerError() throws Exception {
        MockHttpServletResponse response = runWith(new IllegalStateException("boom"));

        assertThat(response.getStatus()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus().value());
    }
}
