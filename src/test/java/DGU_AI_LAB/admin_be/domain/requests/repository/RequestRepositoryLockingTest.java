package DGU_AI_LAB.admin_be.domain.requests.repository;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
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

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * findByIdForUpdate()의 행 잠금이 실제로 동시 접근을 직렬화하는지 검증한다.
 * 테스트 메서드 자체는 트랜잭션으로 감싸지 않아(NOT_SUPPORTED) 스레드별 TransactionTemplate이
 * 각자 독립적으로 커밋되도록 한다 — @DataJpaTest 기본 롤백 트랜잭션 안에서는 두 스레드가
 * 서로의 커밋을 볼 수 없어 이 테스트의 목적(실제 잠금 검증)을 달성할 수 없다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RequestRepositoryLockingTest {

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResourceGroupRepository resourceGroupRepository;

    @Autowired
    private ContainerImageRepository containerImageRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long requestId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("lockingtest@dgu.ac.kr")
                .password("encoded")
                .name("잠금테스트")
                .studentId("2021009999")
                .phone("010-9999-0000")
                .department("컴퓨터공학과")
                .build());

        ResourceGroup resourceGroup = resourceGroupRepository.save(ResourceGroup.builder()
                .resourceGroupName("Locking Test Server")
                .description("잠금 테스트용")
                .serverName("server-lock")
                .build());

        ContainerImage containerImage = containerImageRepository.save(ContainerImage.builder()
                .imageName("pytorch")
                .imageVersion("2.1.0")
                .cudaVersion("11.8")
                .description("잠금 테스트용 이미지")
                .build());

        Request request = requestRepository.save(Request.builder()
                .ubuntuUsername("lockingtestuser")
                .ubuntuPassword("hashedPw")
                .ubuntuPasswordBase64("base64Pw")
                .volumeSizeGiB(50L)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .usagePurpose("동시성 테스트")
                .formAnswers("{}")
                .user(user)
                .resourceGroup(resourceGroup)
                .containerImage(containerImage)
                .build());

        requestId = request.getRequestId();
    }

    @Test
    @DisplayName("두 트랜잭션이 동시에 findByIdForUpdate를 호출하면, 두 번째는 첫 번째가 커밋할 때까지 대기했다가 변경된 상태를 본다")
    void findByIdForUpdate_blocksConcurrentAccess_untilFirstTransactionCommits() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        long holdMillis = 500;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // 첫 번째 관리자의 승인 시도: 행 잠금을 잡고 holdMillis만큼 "외부 API 호출하는 척" 대기 후 PROCESSING으로 전환
            Future<?> first = executor.submit(() -> tx.executeWithoutResult(status -> {
                Request req = requestRepository.findByIdForUpdate(requestId).orElseThrow();
                lockAcquired.countDown();
                try {
                    Thread.sleep(holdMillis);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                req.markAsProcessing();
            }));

            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            // 두 번째 관리자의 동시 승인 시도: 첫 번째가 락을 쥔 직후 바로 같은 행을 조회 시도
            long start = System.nanoTime();
            AtomicReference<Status> secondSeenStatus = new AtomicReference<>();
            Future<?> second = executor.submit(() -> tx.executeWithoutResult(status -> {
                Request req = requestRepository.findByIdForUpdate(requestId).orElseThrow();
                secondSeenStatus.set(req.getStatus());
            }));

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            // 잠금이 실제로 걸렸다면: (1) 두 번째는 첫 번째 커밋 이후 상태(PROCESSING)를 봐야 한다.
            // 이게 핵심 증거다 — 잠금이 없었다면 두 번째는 커밋 전 상태인 PENDING을 읽었을 것이다.
            // (2) 지연 시간도 블로킹의 보조 증거로 함께 확인하되, 스레드 스케줄링 변동을 감안해 느슨하게 검증한다.
            assertThat(secondSeenStatus.get()).isEqualTo(Status.PROCESSING);
            assertThat(elapsedMillis).isGreaterThanOrEqualTo(holdMillis / 2);
        } finally {
            executor.shutdown();
        }
    }
}
