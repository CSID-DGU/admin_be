package DGU_AI_LAB.admin_be.domain.dashboard.controller;

import DGU_AI_LAB.admin_be.domain.dashboard.service.DashboardService;
import DGU_AI_LAB.admin_be.domain.monitoring.dto.response.MyGpuUsageResponseDTO;
import DGU_AI_LAB.admin_be.domain.monitoring.service.MyGpuUsageService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = DashboardController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import(DashboardControllerTest.PrincipalConfig.class)
class DashboardControllerTest extends WebMvcTestSupport {

    /** 보안 설정을 끈 슬라이스라 @AuthenticationPrincipal 대신 로그인한 사용자(id=1)를 직접 꽂는다. */
    @TestConfiguration
    static class PrincipalConfig {
        @Bean
        WebMvcConfigurer principalResolver() {
            return new WebMvcConfigurer() {
                @Override
                public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(new HandlerMethodArgumentResolver() {
                        @Override
                        public boolean supportsParameter(MethodParameter p) {
                            return p.getParameterType() == CustomUserDetails.class;
                        }

                        @Override
                        public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest r,
                                                      WebDataBinderFactory b) {
                            User user = mock(User.class);
                            when(user.getUserId()).thenReturn(1L);
                            return new CustomUserDetails(user);
                        }
                    });
                }
            };
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DashboardService dashboardService;
    @MockitoBean private MyGpuUsageService myGpuUsageService;

    @Test
    @DisplayName("GET /me/servers/{id}/gpus: 내 신청이면 200과 GPU 목록")
    void ok() throws Exception {
        when(myGpuUsageService.getMyGpuUsage(10L, 1L)).thenReturn(
                MyGpuUsageResponseDTO.builder().nodeName("farm6").gpus(List.of()).processesAvailable(true).build());

        mockMvc.perform(get("/api/dashboard/me/servers/10/gpus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeName").value("farm6"))
                .andExpect(jsonPath("$.data.processesAvailable").value(true));
    }

    @Test
    @DisplayName("GET /me/servers/{id}/gpus: 남의 신청이면 403")
    void forbidden() throws Exception {
        when(myGpuUsageService.getMyGpuUsage(10L, 1L)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/dashboard/me/servers/10/gpus")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /me/servers/{id}/gpus: 없거나 실행 중이 아닌 신청이면 404")
    void notFound() throws Exception {
        when(myGpuUsageService.getMyGpuUsage(10L, 1L)).thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/dashboard/me/servers/10/gpus")).andExpect(status().isNotFound());
    }
}
