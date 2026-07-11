package DGU_AI_LAB.admin_be.global.actuator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * H-9: Actuator health show-details: when_authorized 검증
 *
 * 테스트 환경에서는 Redis/DB가 완전히 기동되지 않으므로 status는 DOWN(503)이 될 수 있음.
 * 중요한 것은 HTTP 상태 코드가 아니라, 미인증 요청에서 components(상세 정보)가 노출되지 않는 것.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health",
        "management.endpoint.health.show-details=when_authorized"
})
class ActuatorHealthSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("미인증 요청 — 상세 정보 미노출")
    class Unauthenticated {

        @Test
        @DisplayName("미인증 상태에서 /actuator/health는 status 필드만 반환한다")
        void health_unauthenticated_returnsStatusOnly() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.components").doesNotExist());
        }

        @Test
        @DisplayName("미인증 상태에서 DB 연결 정보(components.db)가 응답에 포함되지 않는다")
        void health_unauthenticated_doesNotExposeDatasource() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(jsonPath("$.components.db").doesNotExist());
        }

        @Test
        @DisplayName("미인증 상태에서 Redis 정보(components.redis)가 응답에 포함되지 않는다")
        void health_unauthenticated_doesNotExposeRedis() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(jsonPath("$.components.redis").doesNotExist());
        }

        @Test
        @DisplayName("미인증 상태에서 diskSpace 정보가 응답에 포함되지 않는다")
        void health_unauthenticated_doesNotExposeDiskSpace() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(jsonPath("$.components.diskSpace").doesNotExist());
        }
    }

    @Nested
    @DisplayName("ADMIN 인증 요청 — 상세 정보 포함")
    class AuthenticatedAdmin {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN 인증 시 /actuator/health에서 components(상세 정보)가 포함된다")
        void health_authenticatedAdmin_returnsComponents() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.components").exists());
        }
    }
}
