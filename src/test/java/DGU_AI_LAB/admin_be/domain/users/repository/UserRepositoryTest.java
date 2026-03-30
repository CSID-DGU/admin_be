package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
    @DisplayName("findUsersForHardDelete")
    class FindUsersForHardDelete {

        @Test
        @DisplayName("Soft delete 후 1년이 지나지 않은 유저는 Hard delete 대상이 아니다")
        void findUsersForHardDelete_returnsEmpty_whenDeletedWithinOneYear() {
            user1.withdraw();
            userRepository.save(user1);
            userRepository.flush();

            List<User> result = userRepository.findUsersForHardDelete(LocalDateTime.now().minusYears(1));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Soft delete된 지 1년이 지난 유저를 Hard delete 대상으로 조회한다")
        void findUsersForHardDelete_returnsUser_whenDeletedBeforeOneYearThreshold() {
            user1.withdraw();
            ReflectionTestUtils.setField(user1, "deletedAt", LocalDateTime.now().minusYears(2));
            userRepository.save(user1);
            userRepository.flush();

            List<User> result = userRepository.findUsersForHardDelete(LocalDateTime.now().minusYears(1));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmail()).isEqualTo("active@dgu.ac.kr");
        }
    }
}
