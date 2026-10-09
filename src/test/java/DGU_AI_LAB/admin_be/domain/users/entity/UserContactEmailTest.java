package DGU_AI_LAB.admin_be.domain.users.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User 자주 사용하는 이메일")
class UserContactEmailTest {

    private static User user(String contactEmail) {
        return User.builder().email("hong@dgu.ac.kr").name("홍길동").contactEmail(contactEmail).build();
    }

    @Test
    @DisplayName("따로 적은 주소가 있으면 학교 이메일과 함께 이 사람의 이메일로 본다")
    void knownEmailsIncludeContactEmail() {
        assertThat(user(" hong@gmail.com ").knownEmails()).containsExactly("hong@dgu.ac.kr", "hong@gmail.com");
    }

    @Test
    @DisplayName("비었거나 학교 이메일과 같으면 따로 없는 것으로 둔다")
    void blankOrSameIsNone() {
        assertThat(user(null).getContactEmail()).isNull();
        assertThat(user("  ").getContactEmail()).isNull();
        assertThat(user("HONG@dgu.ac.kr").getContactEmail()).isNull();
        assertThat(user(null).knownEmails()).containsExactly("hong@dgu.ac.kr");
    }

    @Test
    @DisplayName("바꿀 때도 같은 규칙으로 정리하고, 비우면 지운다")
    void updateNormalizes() {
        User user = user("hong@gmail.com");

        user.updateContactEmail("hong@naver.com");
        assertThat(user.getContactEmail()).isEqualTo("hong@naver.com");

        user.updateContactEmail("");
        assertThat(user.getContactEmail()).isNull();
    }
}
