package DGU_AI_LAB.admin_be.domain.users.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("test@dgu.ac.kr")
                .password("encodedPassword")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1234-5678")
                .department("컴퓨터공학과")
                .build();
    }

    @Nested
    @DisplayName("Builder 기본값")
    class DefaultValues {

        @Test
        @DisplayName("기본 역할은 USER이다")
        void defaultRole_isUser() {
            assertThat(user.getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("기본 활성화 상태는 true이다")
        void defaultIsActive_isTrue() {
            assertThat(user.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("생성 시 lastLoginAt이 설정된다")
        void lastLoginAt_isSetOnCreation() {
            assertThat(user.getLastLoginAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("updatePassword")
    class UpdatePassword {

        @Test
        @DisplayName("새 비밀번호로 업데이트한다")
        void updatePassword_changesPassword() {
            user.updatePassword("newEncodedPassword");

            assertThat(user.getPassword()).isEqualTo("newEncodedPassword");
        }
    }

    @Nested
    @DisplayName("updatePhone")
    class UpdatePhone {

        @Test
        @DisplayName("새 전화번호로 업데이트한다")
        void updatePhone_changesPhone() {
            user.updatePhone("010-9999-8888");

            assertThat(user.getPhone()).isEqualTo("010-9999-8888");
        }
    }

    @Nested
    @DisplayName("updateUserInfo")
    class UpdateUserInfo {

        @Test
        @DisplayName("encodedPassword가 null이 아니면 비밀번호를 업데이트한다")
        void updateUserInfo_updatesPassword_whenNotNull() {
            user.updateUserInfo("newPw", null);

            assertThat(user.getPassword()).isEqualTo("newPw");
        }

        @Test
        @DisplayName("isActive가 false이면 비활성화한다")
        void updateUserInfo_deactivates_whenIsActiveFalse() {
            user.updateUserInfo(null, false);

            assertThat(user.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("null 값은 기존 값을 유지한다")
        void updateUserInfo_doesNotChange_whenNull() {
            user.updateUserInfo(null, null);

            assertThat(user.getPassword()).isEqualTo("encodedPassword");
            assertThat(user.getIsActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("recordLogin")
    class RecordLogin {

        @Test
        @DisplayName("로그인 기록 시 lastLoginAt이 갱신된다")
        void recordLogin_updatesLastLoginAt() throws InterruptedException {
            var before = user.getLastLoginAt();
            Thread.sleep(10); // ensure time difference
            user.recordLogin();

            assertThat(user.getLastLoginAt()).isAfterOrEqualTo(before);
        }
    }

    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        @Test
        @DisplayName("탈퇴하면 isActive가 false가 되고 deletedAt이 설정된다")
        void withdraw_setsInactiveAndDeletedAt() {
            user.withdraw();

            assertThat(user.getIsActive()).isFalse();
            assertThat(user.getDeletedAt()).isNotNull();
        }
    }
}
