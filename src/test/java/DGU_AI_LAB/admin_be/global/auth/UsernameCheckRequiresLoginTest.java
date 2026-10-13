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
 * 이름 중복 확인은 내부 토큰 경로(/api/requests/config/**) 안에 있지만 로그인한 사용자만 부른다.
 * 필터 두 개와 경로 규칙이 함께 맞아야 하므로 실제 서버로 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UsernameCheckRequiresLoginTest {

    private static final String PATH = "/api/requests/config/check-username?username=someone";

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
    @DisplayName("로그인하지 않으면 이름이 쓰이고 있는지 알려 주지 않는다")
    void withoutLogin_isRejected() {
        ResponseEntity<String> response = restTemplate.getForEntity(PATH, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).doesNotContain("available");
    }

    @Test
    @DisplayName("로그인한 사용자는 내부 토큰 없이 이름 중복을 확인한다")
    void withLogin_answers() {
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
                PATH, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("available");
    }
}
