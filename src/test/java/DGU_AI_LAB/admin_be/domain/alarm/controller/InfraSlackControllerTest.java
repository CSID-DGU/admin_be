package DGU_AI_LAB.admin_be.domain.alarm.controller;

import DGU_AI_LAB.admin_be.domain.alarm.service.InfraAlarmService;
import DGU_AI_LAB.admin_be.support.WebMvcTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = InfraSlackController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class InfraSlackControllerTest extends WebMvcTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InfraAlarmService infraAlarmService;

    @Nested
    @DisplayName("POST /api/internal/slack/notify")
    class NotifySlack {

        @Test
        @DisplayName("유효한 요청이면 200 OK와 SuccessResponse를 반환한다")
        void notify_returns200_whenValidRequest() throws Exception {
            doNothing().when(infraAlarmService).enqueue(anyString(), anyString());

            mockMvc.perform(post("/api/internal/slack/notify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"webhookUrl\":\"https://hooks.slack.com/test\",\"message\":\"테스트 메시지\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("유효한 요청이면 InfraAlarmService.enqueue()가 올바른 인자로 호출된다")
        void notify_callsEnqueueWithCorrectArgs() throws Exception {
            doNothing().when(infraAlarmService).enqueue(anyString(), anyString());

            mockMvc.perform(post("/api/internal/slack/notify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"webhookUrl\":\"https://hooks.slack.com/abc\",\"message\":\"GPU 경고\"}"))
                    .andExpect(status().isOk());

            verify(infraAlarmService).enqueue("https://hooks.slack.com/abc", "GPU 경고");
        }

        @Test
        @DisplayName("webhookUrl이 없으면 400 Bad Request를 반환한다")
        void notify_returns400_whenWebhookUrlMissing() throws Exception {
            mockMvc.perform(post("/api/internal/slack/notify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"메시지만 있음\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("message가 없으면 400 Bad Request를 반환한다")
        void notify_returns400_whenMessageMissing() throws Exception {
            mockMvc.perform(post("/api/internal/slack/notify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"webhookUrl\":\"https://hooks.slack.com/test\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("빈 문자열 webhookUrl이면 400 Bad Request를 반환한다")
        void notify_returns400_whenWebhookUrlBlank() throws Exception {
            mockMvc.perform(post("/api/internal/slack/notify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"webhookUrl\":\"\",\"message\":\"메시지\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("빈 문자열 message이면 400 Bad Request를 반환한다")
        void notify_returns400_whenMessageBlank() throws Exception {
            mockMvc.perform(post("/api/internal/slack/notify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"webhookUrl\":\"https://hooks.slack.com/test\",\"message\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
