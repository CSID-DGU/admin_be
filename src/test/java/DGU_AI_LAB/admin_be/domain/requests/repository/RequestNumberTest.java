package DGU_AI_LAB.admin_be.domain.requests.repository;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 신청 번호가 빠짐없이 이어지는지 검증한다. 되돌림과 동시 저장을 실제 커밋으로 봐야 하므로 테스트 메서드는
 * 트랜잭션으로 감싸지 않는다(NOT_SUPPORTED).
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RequestNumberTest {

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

    private User user;
    private ResourceGroup resourceGroup;
    private ContainerImage image;

    @BeforeEach
    void setUp() {
        String unique = String.valueOf(System.nanoTime());
        user = userRepository.save(User.builder()
                .email("number-" + unique + "@test.com").password("pw").name("번호테스트")
                .studentId("S" + unique).phone("010-0000-0000").department("CSE").build());
        resourceGroup = resourceGroupRepository.save(ResourceGroup.builder()
                .resourceGroupName("number-rg-" + unique).serverName("FARM").build());
        image = containerImageRepository.save(ContainerImage.builder()
                .imageName("number-image-" + unique).imageVersion("1.0").cudaVersion("12.1").description("test").build());
    }

    /** 커밋된 행이 같은 H2를 쓰는 다른 테스트에 보이지 않게 지운다. */
    @AfterEach
    void tearDown() {
        requestRepository.deleteAll(requestRepository.findAllByUser_UserId(user.getUserId()));
        userRepository.deleteById(user.getUserId());
        resourceGroupRepository.deleteById(resourceGroup.getRsgroupId());
        containerImageRepository.deleteById(image.getImageId());
    }

    @Test
    @DisplayName("저장한 순서대로 1씩 커진다")
    void consecutiveSaves_getConsecutiveNumbers() {
        Long first = requestRepository.save(newRequest()).getRequestId();
        Long second = requestRepository.save(newRequest()).getRequestId();

        assertThat(second).isEqualTo(first + 1);
    }

    @Test
    @DisplayName("저장이 되돌려지면 그 번호를 다음 신청이 받는다 — 번호가 빠지지 않는다")
    void rolledBackSave_returnsItsNumber() {
        Long before = requestRepository.save(newRequest()).getRequestId();

        Long rolledBack = new TransactionTemplate(transactionManager).execute(status -> {
            Long number = requestRepository.saveAndFlush(newRequest()).getRequestId();
            status.setRollbackOnly();
            return number;
        });
        Long after = requestRepository.save(newRequest()).getRequestId();

        assertThat(rolledBack).isEqualTo(before + 1);
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("신청 행을 지워도 같은 번호가 다시 나오지 않는다")
    void deletedRequest_numberIsNotReused() {
        Request last = requestRepository.save(newRequest());
        requestRepository.delete(last);

        Long next = requestRepository.save(newRequest()).getRequestId();

        assertThat(next).isEqualTo(last.getRequestId() + 1);
    }

    @Test
    @DisplayName("동시에 저장해도 번호가 겹치거나 빠지지 않는다")
    void concurrentSaves_getDistinctConsecutiveNumbers() throws Exception {
        int threads = 8;
        Long before = requestRepository.save(newRequest()).getRequestId();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return new TransactionTemplate(transactionManager)
                            .execute(status -> requestRepository.saveAndFlush(newRequest()).getRequestId());
                }));
            }
            start.countDown();

            List<Long> numbers = new ArrayList<>();
            for (Future<Long> future : futures) {
                numbers.add(future.get(10, TimeUnit.SECONDS));
            }

            List<Long> expected = new ArrayList<>();
            for (long n = before + 1; n <= before + threads; n++) {
                expected.add(n);
            }
            assertThat(numbers).containsExactlyInAnyOrderElementsOf(expected);
        } finally {
            pool.shutdownNow();
        }
    }

    private Request newRequest() {
        return Request.builder()
                .expiresAt(LocalDateTime.now().plusDays(30)).usagePurpose("연구").formAnswers("{}")
                .user(user).resourceGroup(resourceGroup).containerImage(image).build();
    }
}
