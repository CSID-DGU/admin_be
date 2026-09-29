package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        user1 = userRepository.save(User.builder()
                .email("active@dgu.ac.kr")
                .password("encoded1")
                .name("홍길동")
                .studentId("2021001234")
                .phone("010-1111-2222")
                .department("컴퓨터공학과")
                .build());

        user2 = userRepository.save(User.builder()
                .email("user2@dgu.ac.kr")
                .password("encoded2")
                .name("이순신")
                .studentId("2021005678")
                .phone("010-5678-1234")
                .department("전자공학과")
                .build());
    }

    @Nested
    @DisplayName("findByEmail")
    class FindByEmail {

        @Test
        @DisplayName("존재하는 이메일로 조회하면 유저를 반환한다")
        void findByEmail_returnsUser_whenExists() {
            Optional<User> result = userRepository.findByEmail("active@dgu.ac.kr");

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("홍길동");
        }

        @Test
        @DisplayName("존재하지 않는 이메일로 조회하면 빈 Optional을 반환한다")
        void findByEmail_returnsEmpty_whenNotExists() {
            Optional<User> result = userRepository.findByEmail("notexist@dgu.ac.kr");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("recordLogin")
    class RecordLogin {

        @Test
        @DisplayName("트랜잭션 밖에서 불러도 로그인 시각이 DB에 저장되고, 다른 칸은 건드리지 않는다")
        void recordLogin_persistsLastLoginAt() {
            LocalDateTime loggedInAt = LocalDateTime.of(2026, 9, 29, 10, 0);

            int updated = userRepository.recordLogin(user1.getUserId(), loggedInAt);
            entityManager.clear();

            assertThat(updated).isEqualTo(1);
            User reloaded = userRepository.findById(user1.getUserId()).orElseThrow();
            assertThat(reloaded.getLastLoginAt()).isEqualTo(loggedInAt);
            assertThat(reloaded.getPassword()).isEqualTo("encoded1");
            assertThat(userRepository.findById(user2.getUserId()).orElseThrow().getLastLoginAt())
                    .isNotEqualTo(loggedInAt);
        }
    }

    @Nested
    @DisplayName("findInactiveUsers")
    class FindInactiveUsers {

        @Test
        @DisplayName("마지막 로그인이 기준일 이전인 활성 유저를 조회한다")
        void findInactiveUsers_returnsUsersInactiveBeforeThreshold() {
            ReflectionTestUtils.setField(user1, "lastLoginAt", LocalDateTime.now().minusMonths(4));
            userRepository.save(user1);
            userRepository.flush();

            LocalDateTime thresholdDate = LocalDateTime.now().minusMonths(3);
            List<User> result = userRepository.findInactiveUsers(thresholdDate);

            assertThat(result).extracting(User::getEmail).contains("active@dgu.ac.kr");
        }
    }

    @Nested
    @DisplayName("replaceWeakUbuntuPasswordHash")
    class ReplaceWeakUbuntuPasswordHash {

        private static final String CURRENT = "$6$rounds=656000$";

        @Test
        @DisplayName("비어 있거나 옛 강도 해시만 바꾸고, 지금 강도 해시는 그대로 둔다")
        void replacesOnlyMissingOrWeakHash() {
            user2.changeUbuntuPasswordHash("$6$oldsalt$old");
            userRepository.saveAndFlush(user2);

            assertThat(userRepository.replaceWeakUbuntuPasswordHash(user1.getUserId(), CURRENT + "s$new1", CURRENT)).isEqualTo(1);
            assertThat(userRepository.replaceWeakUbuntuPasswordHash(user2.getUserId(), CURRENT + "s$new2", CURRENT)).isEqualTo(1);
            assertThat(userRepository.replaceWeakUbuntuPasswordHash(user2.getUserId(), CURRENT + "s$new3", CURRENT)).isZero();
            entityManager.clear();

            assertThat(userRepository.findById(user2.getUserId()).orElseThrow().getUbuntuPasswordHash())
                    .isEqualTo(CURRENT + "s$new2");
        }
    }
}
