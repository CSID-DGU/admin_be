package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
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

import java.time.LocalDate;
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

        private void setCreatedAt(User user, LocalDateTime time) {
            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE users SET created_at = :t WHERE user_id = :id")
                    .setParameter("t", time).setParameter("id", user.getUserId()).executeUpdate();
            entityManager.clear();
        }

        @Test
        @DisplayName("가입 시각이 기준일 이전인 활성 유저를 조회한다 — 최근 로그인은 보지 않는다")
        void findInactiveUsers_returnsUsersSignedUpBeforeThreshold() {
            ReflectionTestUtils.setField(user1, "lastLoginAt", LocalDateTime.now().minusDays(1));
            userRepository.saveAndFlush(user1);
            setCreatedAt(user1, LocalDateTime.now().minusYears(2));

            List<User> result = userRepository.findInactiveUsers(LocalDateTime.now().minusYears(1), Status.openStatuses());

            assertThat(result).extracting(User::getEmail).contains("active@dgu.ac.kr");
        }

        @Test
        @DisplayName("졸업 예정일이 있어도 조회한다 — 졸업 예정일을 미뤄 1년 규칙을 피할 수 없다")
        void findInactiveUsers_includesUsersWithGraduationDate() {
            user1.updateExpectedGraduationDate(LocalDate.now().plusYears(1));
            userRepository.saveAndFlush(user1);
            setCreatedAt(user1, LocalDateTime.now().minusYears(2));

            List<User> result = userRepository.findInactiveUsers(LocalDateTime.now().minusYears(1), Status.openStatuses());

            assertThat(result).extracting(User::getEmail).contains("active@dgu.ac.kr");
        }

        @Test
        @DisplayName("관리자는 조회하지 않는다")
        void findInactiveUsers_excludesAdmins() {
            user1.changeRole(Role.ADMIN);
            userRepository.saveAndFlush(user1);
            setCreatedAt(user1, LocalDateTime.now().minusYears(2));

            List<User> result = userRepository.findInactiveUsers(LocalDateTime.now().minusYears(1), Status.openStatuses());

            assertThat(result).extracting(User::getEmail).doesNotContain("active@dgu.ac.kr");
        }

        @Test
        @DisplayName("끝나지 않은 신청(컨테이너 포함)이 있으면 조회하지 않는다")
        void findInactiveUsers_excludesUsersWithOpenRequests() {
            ResourceGroup rg = entityManager.persist(ResourceGroup.builder()
                    .resourceGroupName("3090").description("GPU").serverName("FARM").build());
            ContainerImage image = entityManager.persist(ContainerImage.builder()
                    .imageName("pytorch").imageVersion("2.1.0").cudaVersion("11.8").description("PyTorch").build());
            entityManager.persist(Request.builder()
                    .expiresAt(LocalDateTime.now().plusDays(30)).usagePurpose("연구").formAnswers("{}")
                    .user(user1).resourceGroup(rg).containerImage(image).build());
            entityManager.flush();
            setCreatedAt(user1, LocalDateTime.now().minusYears(2));

            List<User> result = userRepository.findInactiveUsers(LocalDateTime.now().minusYears(1), Status.openStatuses());

            assertThat(result).extracting(User::getEmail).doesNotContain("active@dgu.ac.kr");
        }

        @Test
        @DisplayName("최근 가입자는 조회하지 않는다")
        void findInactiveUsers_excludesRecentSignups() {
            List<User> result = userRepository.findInactiveUsers(LocalDateTime.now().minusYears(1), Status.openStatuses());

            assertThat(result).extracting(User::getEmail).doesNotContain("active@dgu.ac.kr", "user2@dgu.ac.kr");
        }
    }

    @Nested
    @DisplayName("findGraduatingUsers")
    class FindGraduatingUsers {

        private final LocalDate today = LocalDate.of(2026, 11, 9);

        private void graduate(User user, LocalDate date) {
            user.updateExpectedGraduationDate(date);
            userRepository.saveAndFlush(user);
        }

        @Test
        @DisplayName("졸업 예정일이 기준일보다 이른 활성 일반 사용자만 조회한다")
        void returnsOnlyBeforeCutoff() {
            graduate(user1, today.minusDays(31));
            graduate(user2, today);

            List<User> result = userRepository.findGraduatingUsers(today, Status.openStatuses());

            assertThat(result).extracting(User::getEmail).containsExactly("active@dgu.ac.kr");
        }

        @Test
        @DisplayName("졸업 예정일이 없거나 관리자거나 비활성이면 조회하지 않는다")
        void excludesNullAdminInactive() {
            graduate(user2, today.minusDays(40));
            user2.changeRole(Role.ADMIN);
            userRepository.saveAndFlush(user2);
            // user1: 졸업 예정일 없음
            User inactive = userRepository.save(User.builder().email("off@dgu.ac.kr").password("p")
                    .name("비활성").studentId("2021009999").phone("010-9999-9999").department("컴퓨터공학과").build());
            graduate(inactive, today.minusDays(40));
            inactive.deactivate();
            userRepository.saveAndFlush(inactive);

            assertThat(userRepository.findGraduatingUsers(today, Status.openStatuses())).isEmpty();
        }

        @Test
        @DisplayName("끝나지 않은 신청이 있으면 조회하지 않는다")
        void excludesOpenRequests() {
            graduate(user1, today.minusDays(40));
            persistRequest(user1);

            assertThat(userRepository.findGraduatingUsers(today, Status.openStatuses())).isEmpty();
        }
    }

    @Nested
    @DisplayName("findHomeReminderCandidates")
    class FindHomeReminderCandidates {

        private final LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
        private final LocalDateTime to = LocalDateTime.of(2026, 10, 2, 0, 0);

        private void withAccount(User user) {
            user.assignUbuntuAccount(60001L + user.getUserId(), 60001L + user.getUserId());
            ReflectionTestUtils.setField(user, "ubuntuUsername", "u" + user.getUserId());
            userRepository.saveAndFlush(user);
        }

        private Long deletedRequest(User user, LocalDateTime endedAt) {
            Long id = persistRequest(user);
            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE requests SET status = 'DELETED', approved_at = :t, updated_at = :t WHERE request_id = :id")
                    .setParameter("t", endedAt).setParameter("id", id).executeUpdate();
            entityManager.clear();
            return id;
        }

        @Test
        @DisplayName("마지막 컨테이너가 [from, to) 안에 끝난 사용자만 조회한다")
        void windowIsHalfOpen() {
            withAccount(user1);
            withAccount(user2);
            deletedRequest(user1, from);              // 포함(경계)
            deletedRequest(user2, to);                // 제외(상한 미포함)

            List<User> result = userRepository.findHomeReminderCandidates(from, to, Status.openStatuses(), User.PRIMARY_SERVER);

            assertThat(result).extracting(User::getEmail).containsExactly("active@dgu.ac.kr");
            // 안내 후보는 삭제 후보의 부분집합이어야 한다 — 두 질의의 공통 조건이 어긋나면 여기서 드러난다.
            assertThat(userRepository.findHomeCleanupCandidates(to.plusDays(1), Status.openStatuses(), User.PRIMARY_SERVER))
                    .containsAll(result);
        }

        @Test
        @DisplayName("더 나중에 끝난 컨테이너가 있으면 마지막 시각이 창 밖이라 조회하지 않는다")
        void usesLatestEnd() {
            withAccount(user1);
            deletedRequest(user1, from.plusHours(1));
            deletedRequest(user1, to.plusDays(5));

            assertThat(userRepository.findHomeReminderCandidates(from, to, Status.openStatuses(), User.PRIMARY_SERVER)).isEmpty();
        }

        @Test
        @DisplayName("계정이 없거나 끝나지 않은 신청이 있거나 탈퇴한 사용자는 조회하지 않는다")
        void excludes() {
            deletedRequest(user1, from.plusHours(1));          // 계정 없음
            withAccount(user2);
            deletedRequest(user2, from.plusHours(1));
            persistRequest(user2);                              // 열린 신청
            assertThat(userRepository.findHomeReminderCandidates(from, to, Status.openStatuses(), User.PRIMARY_SERVER)).isEmpty();

            User gone = userRepository.save(User.builder().email("gone@dgu.ac.kr").password("p")
                    .name("탈퇴").studentId("2021008888").phone("010-8888-8888").department("컴퓨터공학과").build());
            withAccount(gone);
            deletedRequest(gone, from.plusHours(1));
            gone = userRepository.findById(gone.getUserId()).orElseThrow();
            gone.withdraw();
            userRepository.saveAndFlush(gone);
            assertThat(userRepository.findHomeReminderCandidates(from, to, Status.openStatuses(), User.PRIMARY_SERVER)).isEmpty();
        }

        @Test
        @DisplayName("비활성화된 사용자에게는 안내하지 않는다")
        void excludesInactive() {
            withAccount(user1);
            deletedRequest(user1, from.plusHours(1));
            user1 = userRepository.findById(user1.getUserId()).orElseThrow();
            user1.deactivate();
            userRepository.saveAndFlush(user1);

            assertThat(userRepository.findHomeReminderCandidates(from, to, Status.openStatuses(), User.PRIMARY_SERVER)).isEmpty();
        }
    }

    @Test
    @DisplayName("existsByUbuntuUsername은 다른 서버 계정에 이미 있는 유저네임도 사용 중으로 본다")
    void existsByUbuntuUsername_seesAccountNames() {
        user1.assignUbuntuAccount("LAB", 10002L, 10002L);
        ReflectionTestUtils.setField(user1.ubuntuAccount("LAB").orElseThrow(), "username", "legacyname");
        userRepository.saveAndFlush(user1);
        entityManager.clear();

        assertThat(userRepository.existsByUbuntuUsername("legacyname")).isTrue();
        assertThat(userRepository.existsByUbuntuUsername("nobody-has-this")).isFalse();
    }

    @Test
    @DisplayName("홈 삭제 후보는 기본 서버의 신청만 본다 — 다른 서버 컨테이너가 끝났다고 그 서버 홈을 지우지 않는다")
    void homeCleanupCandidates_areScopedToServer() {
        user1.assignUbuntuAccount(60001L, 60001L);
        ReflectionTestUtils.setField(user1, "ubuntuUsername", "scoped1");
        userRepository.saveAndFlush(user1);
        Long id = persistRequest(user1);
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE requests SET status = 'DELETED', approved_at = :t, updated_at = :t WHERE request_id = :id")
                .setParameter("t", LocalDateTime.of(2026, 1, 1, 0, 0)).setParameter("id", id).executeUpdate();
        entityManager.clear();
        LocalDateTime cutoff = LocalDateTime.of(2026, 6, 1, 0, 0);

        assertThat(userRepository.findHomeCleanupCandidates(cutoff, Status.openStatuses(), "FARM")).contains(user1);
        assertThat(userRepository.findHomeCleanupCandidates(cutoff, Status.openStatuses(), "LAB")).doesNotContain(user1);
    }

    private Long persistRequest(User user) {
        ResourceGroup rg = entityManager.persist(ResourceGroup.builder()
                .resourceGroupName("rg" + System.nanoTime()).description("GPU").serverName("FARM").build());
        ContainerImage image = entityManager.persist(ContainerImage.builder()
                .imageName("img" + System.nanoTime()).imageVersion("1").cudaVersion("11.8").description("d").build());
        Request r = entityManager.persist(Request.builder()
                .expiresAt(LocalDateTime.now().plusDays(30)).usagePurpose("연구").formAnswers("{}")
                .user(user).resourceGroup(rg).containerImage(image).build());
        entityManager.flush();
        return r.getRequestId();
    }

    @Nested
    @DisplayName("replaceWeakUbuntuPasswordHash")
    class ReplaceWeakUbuntuPasswordHash {

        private static final String CURRENT = "$6$rounds=100000$";

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
