package DGU_AI_LAB.admin_be.domain.users.repository;

import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * lockActiveUsersByRole()이 두 관리자를 동시에 내리는 트랜잭션을 실제로 직렬화하는지 검증한다.
 * RequestRepositoryLockingTest와 같은 이유로 테스트 메서드는 트랜잭션 밖에서 돌려 스레드별 커밋이 서로 보이게 한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserRepositoryLockingTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long firstAdminId;
    private Long secondAdminId;

    @BeforeEach
    void setUp() {
        firstAdminId = userRepository.save(admin("lock-admin1@dgu.ac.kr", "2021008881")).getUserId();
        secondAdminId = userRepository.save(admin("lock-admin2@dgu.ac.kr", "2021008882")).getUserId();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllById(List.of(firstAdminId, secondAdminId));
    }

    @Test
    @DisplayName("관리자를 내리는 트랜잭션이 잠금을 쥔 동안 다른 트랜잭션은 기다렸다가 내려간 결과를 본다")
    void lockActiveUsersByRole_serializesConcurrentDemotions() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        long holdMillis = 500;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> tx.executeWithoutResult(status -> {
                userRepository.lockActiveUsersByRole(Role.ADMIN);
                lockAcquired.countDown();
                try {
                    Thread.sleep(holdMillis);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                userRepository.findById(firstAdminId).orElseThrow().changeRole(Role.USER);
            }));

            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            AtomicReference<List<Long>> secondSeen = new AtomicReference<>();
            Future<?> second = executor.submit(() -> tx.executeWithoutResult(status ->
                    secondSeen.set(userRepository.lockActiveUsersByRole(Role.ADMIN).stream()
                            .map(User::getUserId)
                            .filter(id -> id.equals(firstAdminId) || id.equals(secondAdminId))
                            .toList())));

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            // 잠금이 없었다면 두 번째는 커밋 전 상태(관리자 둘)를 읽고 자기 대상을 내려 관리자 0명을 만들었을 것이다.
            assertThat(secondSeen.get()).containsExactly(secondAdminId);
        } finally {
            executor.shutdown();
        }
    }

    private static User admin(String email, String studentId) {
        User user = User.builder()
                .email(email)
                .password("encoded")
                .name("잠금관리자")
                .studentId(studentId)
                .phone("010-8888-0000")
                .department("컴퓨터공학과")
                .build();
        user.changeRole(Role.ADMIN);
        return user;
    }
}
