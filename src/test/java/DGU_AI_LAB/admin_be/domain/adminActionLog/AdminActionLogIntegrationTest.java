package DGU_AI_LAB.admin_be.domain.adminActionLog;

import DGU_AI_LAB.admin_be.domain.adminActionLog.entity.AdminActionLog;
import DGU_AI_LAB.admin_be.domain.adminActionLog.repository.AdminActionLogRepository;
import DGU_AI_LAB.admin_be.domain.adminActionLog.service.AdminActionLogWriter;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * 시큐리티 필터·인터셉터·예외 처리기를 모두 지난 결과를 본다. MockMvc 는 afterCompletion 까지 마친 뒤
 * 돌아오므로 응답을 받은 시점에 기록 저장도 끝나 있다.
 * 성공·검증 실패·업무 규칙 실패가 모두 남고, 권한에서 막힌 요청과 어노테이션 없는 API 는 남지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminActionLogIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired AdminActionLogRepository logRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoSpyBean AdminActionLogWriter writer;

    User admin;
    User member;

    @BeforeEach
    void setUp() {
        admin = save("admin@dgu.ac.kr", "관리자", "2020000001");
        admin.changeRole(Role.ADMIN);
        admin = userRepository.save(admin);
        member = save("member@dgu.ac.kr", "사용자", "2020000002");
    }

    @AfterEach
    void tearDown() {
        reset(writer);
        jdbcTemplate.update("delete from admin_action_logs");
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("성공한 역할 변경이 관리자·대상·본문과 함께 남고, 조회 API 로 대상 기준으로 찾힌다")
    void success_isRecorded_andSearchable() throws Exception {
        MockHttpServletResponse response = call(admin, HttpMethod.PATCH,
                "/api/admin/users/" + member.getUserId() + "/role", "{\"role\":\"ADMIN\"}");
        assertThat(response.getStatus()).isEqualTo(200);

        AdminActionLog log = onlyLog();
        assertThat(log.getAction()).isEqualTo("USER_ROLE_CHANGE");
        assertThat(log.getTargetType()).isEqualTo("USER");
        assertThat(log.getTargetId()).isEqualTo(member.getUserId().toString());
        assertThat(log.getAdminId()).isEqualTo(admin.getUserId());
        assertThat(log.getAdminName()).isEqualTo("관리자");
        assertThat(log.getHttpStatus()).isEqualTo(200);
        assertThat(log.getErrorCode()).isNull();
        assertThat(log.getRequestBody()).contains("ADMIN");

        MockHttpServletResponse found = call(admin, HttpMethod.GET,
                "/api/admin/action-logs?targetType=USER&targetId=" + member.getUserId(), null);
        assertThat(found.getStatus()).isEqualTo(200);
        assertThat(found.getContentAsString(StandardCharsets.UTF_8)).contains("\"action\":\"USER_ROLE_CHANGE\"", "\"result\":\"SUCCESS\"",
                "\"role\":\"ADMIN\"");
    }

    @Test
    @DisplayName("검증에 실패한 비밀번호 초기화(400)도 남고, 새 비밀번호는 가려진다")
    void validationFailure_isRecorded_withMaskedBody() {
        MockHttpServletResponse response = call(admin, HttpMethod.PUT,
                "/api/admin/users/" + member.getUserId() + "/password", "{\"newPassword\":\"short\"}");
        assertThat(response.getStatus()).isEqualTo(400);

        AdminActionLog log = onlyLog();
        assertThat(log.getAction()).isEqualTo("USER_PASSWORD_RESET");
        assertThat(log.getHttpStatus()).isEqualTo(400);
        assertThat(log.getErrorCode()).isEqualTo("BAD_REQUEST");
        assertThat(log.getRequestBody()).contains("***").doesNotContain("short");
    }

    @Test
    @DisplayName("업무 규칙에 막힌 요청(409)은 오류 코드와 함께 남는다")
    void businessRejection_isRecorded_withErrorCode() {
        MockHttpServletResponse response = call(admin, HttpMethod.PATCH,
                "/api/admin/users/" + member.getUserId() + "/role", "{\"role\":\"USER\"}");
        assertThat(response.getStatus()).isEqualTo(409);

        AdminActionLog log = onlyLog();
        assertThat(log.getHttpStatus()).isEqualTo(409);
        assertThat(log.getErrorCode()).isEqualTo("USER_ALREADY_HAS_ROLE");
    }

    @Test
    @DisplayName("조회 API 에 failedOnly=true 를 주면 실패한 요청만 돌려준다")
    void failedOnly_returnsOnlyFailures() throws Exception {
        call(admin, HttpMethod.PATCH, "/api/admin/users/" + member.getUserId() + "/role", "{\"role\":\"ADMIN\"}");
        call(admin, HttpMethod.PATCH, "/api/admin/users/" + member.getUserId() + "/role", "{\"role\":\"ADMIN\"}");

        MockHttpServletResponse found = call(admin, HttpMethod.GET,
                "/api/admin/action-logs?adminId=" + admin.getUserId() + "&failedOnly=true", null);

        String body = found.getContentAsString(StandardCharsets.UTF_8);
        assertThat(found.getStatus()).isEqualTo(200);
        assertThat(body).contains("\"httpStatus\":409", "\"result\":\"REJECTED\"")
                .doesNotContain("\"httpStatus\":200", "\"result\":\"SUCCESS\"");
    }

    @Test
    @DisplayName("권한에서 막힌 요청(403)과 어노테이션이 없는 관리자 API 는 남지 않는다")
    void forbiddenAndUnannotated_areNotRecorded() {
        MockHttpServletResponse forbidden = call(member, HttpMethod.PATCH,
                "/api/admin/users/" + admin.getUserId() + "/role", "{\"role\":\"USER\"}");
        assertThat(forbidden.getStatus()).isEqualTo(403);

        MockHttpServletResponse read = call(admin, HttpMethod.GET,
                "/api/admin/users/" + member.getUserId() + "/groups", null);
        assertThat(read.getStatus()).isEqualTo(200);

        assertThat(allLogs()).isEmpty();
    }

    @Test
    @DisplayName("기록 저장이 실패해도 관리자 작업의 응답은 그대로다")
    void writerFailure_doesNotChangeResponse() {
        doThrow(new RuntimeException("db down")).when(writer).write(any());

        MockHttpServletResponse response = call(admin, HttpMethod.PATCH,
                "/api/admin/users/" + member.getUserId() + "/role", "{\"role\":\"ADMIN\"}");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(userRepository.findById(member.getUserId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(allLogs()).isEmpty();
    }

    private User save(String email, String name, String studentId) {
        return userRepository.save(User.builder()
                .email(email).password("encoded").name(name).studentId(studentId)
                .phone("010-1111-2222").department("컴퓨터공학과").build());
    }

    private MockHttpServletResponse call(User as, HttpMethod method, String url, String json) {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.getIssueToken(as.getUserId(), true));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        try {
            return mockMvc.perform(request).andReturn().getResponse();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<AdminActionLog> allLogs() {
        return logRepository.search(null, null, null, null, null, null, null, null, PageRequest.ofSize(100));
    }

    private AdminActionLog onlyLog() {
        List<AdminActionLog> logs = allLogs();
        assertThat(logs).hasSize(1);
        return logs.get(0);
    }
}
