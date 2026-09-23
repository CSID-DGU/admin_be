package DGU_AI_LAB.admin_be.global.auth;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인한 일반 사용자가 관리자 API를 부르면 403이어야 한다.
 * MockMvc는 서블릿 컨테이너의 /error 재전달을 흉내 내지 않아 이 경로를 검증하지 못하므로 실제 서버를 띄운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminPathAccessDeniedTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("일반 사용자 토큰으로 관리자 API를 부르면 403과 ACCESS_DENIED 코드를 받는다")
    void adminPath_withUserRole_returnsForbidden() {
        User user = userRepository.save(User.builder()
                .email("user@dgu.ac.kr")
                .password("encoded")
                .name("일반사용자")
                .studentId("2021001234")
                .phone("010-1111-2222")
                .department("컴퓨터공학과")
                .build());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtProvider.getIssueToken(user.getUserId(), true));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/admin/users/" + user.getUserId() + "/groups",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("ACCESS_DENIED");
    }

    @Test
    @DisplayName("토큰 없이 관리자 API를 부르면 401을 받는다")
    void adminPath_withoutToken_returnsUnauthorized() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/admin/users/1/groups", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
