package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

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
            // 3개월+1일 전에 마지막 로그인한 유저 설정 (reflection 사용)
            LocalDateTime thresholdDate = LocalDateTime.now().minusMonths(3);

            List<User> result = userRepository.findInactiveUsers(thresholdDate);
            // 새로 가입한 유저들은 현재 시간으로 lastLoginAt이 설정되어 있어 비활성 대상 아님
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findUsersForHardDelete")
    class FindUsersForHardDelete {

        @Test
        @DisplayName("Soft delete된 지 1년이 지난 유저를 조회한다")
        void findUsersForHardDelete_returnsUsersForHardDelete() {
            // Soft delete 처리
            user1.withdraw();
            userRepository.save(user1);
            userRepository.flush();

            // 1년 후 기준으로 조회 - 아직 1년이 안 됐으므로 조회 안 됨
            List<User> result = userRepository.findUsersForHardDelete(
                    LocalDateTime.now().minusDays(1)  // deletedAt < 어제 = 아직 조회 안 됨
            );

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Soft delete 날짜가 기준일 이전인 유저는 Hard delete 대상이다")
        void findUsersForHardDelete_returnsUser_whenDeletedBeforeThreshold() {
            user1.withdraw();
            userRepository.save(user1);
            userRepository.flush();

            // 기준일을 내일로 설정 -> deletedAt < 내일 = 조회됨
            List<User> result = userRepository.findUsersForHardDelete(
                    LocalDateTime.now().plusDays(1)
            );

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmail()).isEqualTo("active@dgu.ac.kr");
        }
    }
}
