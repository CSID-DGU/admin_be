package DGU_AI_LAB.admin_be.domain.users.entity;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                .ubuntuUsername("honggildong")
                .build();
    }

    @Nested
    @DisplayName("우분투 계정")
    class UbuntuAccount {

        @Test
        @DisplayName("가입 시 유저네임만 정해지고 UID/GID는 아직 없다 — 승인받지 못할 사용자에게 리눅스 계정을 미리 만들지 않는다")
        void freshUser_hasUsernameButNoAccountYet() {
            assertThat(user.getUbuntuUsername()).isEqualTo("honggildong");
            assertThat(user.hasUbuntuAccount()).isFalse();
        }

        @Test
        @DisplayName("첫 승인에서 받은 UID/GID를 배정하면 계정 보유 상태가 된다")
        void assignUbuntuAccount_marksAccountAsOwned() {
            user.assignUbuntuAccount(20001L, 20001L);

            assertThat(user.hasUbuntuAccount()).isTrue();
            assertThat(user.getUbuntuUid()).isEqualTo(20001L);
            assertThat(user.getUbuntuGid()).isEqualTo(20001L);
        }

        @Test
        @DisplayName("같은 UID/GID를 다시 배정하면 조용히 통과한다 — 동시 승인이 같은 값으로 도달할 수 있다")
        void assignUbuntuAccount_isIdempotentForSameValues() {
            user.assignUbuntuAccount(20001L, 20001L);

            user.assignUbuntuAccount(20001L, 20001L);

            assertThat(user.getUbuntuUid()).isEqualTo(20001L);
        }

        @Test
        @DisplayName("이미 다른 UID가 배정돼 있으면 덮어쓰지 않고 실패한다 — 한 웹 계정이 리눅스 계정 두 개를 가리키면 홈 소유권이 어긋난다")
        void assignUbuntuAccount_rejectsConflictingValues() {
            user.assignUbuntuAccount(20001L, 20001L);

            assertThatThrownBy(() -> user.assignUbuntuAccount(20002L, 20002L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UBUNTU_ACCOUNT_ALREADY_ASSIGNED);
            assertThat(user.getUbuntuUid()).isEqualTo(20001L);
        }

        @Test
        @DisplayName("UID/GID가 없거나 0 이하이면 배정을 거부한다")
        void assignUbuntuAccount_rejectsInvalidIds() {
            assertThatThrownBy(() -> user.assignUbuntuAccount(null, 20001L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UID_ALLOCATION_FAILED);
            assertThatThrownBy(() -> user.assignUbuntuAccount(0L, 20001L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UID_ALLOCATION_FAILED);
        }

        @Test
        @DisplayName("계정을 회수하면 UID/GID만 비우고 유저네임은 남긴다 — 유저네임은 웹 계정에 평생 귀속된다")
        void releaseUbuntuAccount_clearsIdsButKeepsUsername() {
            user.assignUbuntuAccount(20001L, 20001L);

            user.releaseUbuntuAccount();

            assertThat(user.hasUbuntuAccount()).isFalse();
            assertThat(user.getUbuntuUid()).isNull();
            assertThat(user.getUbuntuGid()).isNull();
            assertThat(user.getUbuntuUsername()).isEqualTo("honggildong");
        }
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
