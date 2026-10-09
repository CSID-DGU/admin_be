package DGU_AI_LAB.admin_be.global.event;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.portRequests.repository.PortRequestRepository;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 엔티티 저장이 실제로 커밋 뒤 수신자까지 닿는지 본다. 테스트를 트랜잭션으로 감싸지 않아(NOT_SUPPORTED) 저장마다 커밋된다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(RequestRowChangeListenerTest.Capture.class)
@DisplayName("RequestRowChangeListener")
class RequestRowChangeListenerTest {

    @TestConfiguration
    static class Capture {
        final AtomicInteger afterCommit = new AtomicInteger();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(RequestRowChangedEvent event) {
            afterCommit.incrementAndGet();
        }
    }

    @Autowired private Capture capture;
    @Autowired private RequestRepository requestRepository;
    @Autowired private PortRequestRepository portRequestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ResourceGroupRepository resourceGroupRepository;
    @Autowired private ContainerImageRepository containerImageRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private Request request;
    private ResourceGroup resourceGroup;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("rowchange@dgu.ac.kr").password("encoded").name("행변경")
                .studentId("2021008888").phone("010-8888-0000").department("컴퓨터공학과").build());
        resourceGroup = resourceGroupRepository.save(ResourceGroup.builder()
                .resourceGroupName("Row Change Server").description("행 변경 테스트용").serverName("FARM").build());
        ContainerImage image = containerImageRepository.save(ContainerImage.builder()
                .imageName("row-change-image").imageVersion("1.0").cudaVersion("12.3").description("테스트용").build());
        capture.afterCommit.set(0);
        request = requestRepository.save(Request.builder()
                .expiresAt(LocalDateTime.now().plusDays(30)).usagePurpose("행 변경 테스트").formAnswers("{}")
                .user(user).resourceGroup(resourceGroup).containerImage(image).build());
    }

    /** 저장마다 커밋되므로 남긴 행을 직접 지운다 — 같은 DB를 쓰는 다른 테스트에 남으면 안 된다. */
    @AfterEach
    void tearDown() {
        portRequestRepository.deleteAll();
        requestRepository.deleteAll();
        userRepository.deleteAll();
        resourceGroupRepository.deleteAll();
        containerImageRepository.deleteAll();
    }

    @Test
    @DisplayName("신청이 새로 저장되면 커밋 뒤에 알린다")
    void newRequestIsAnnouncedAfterCommit() {
        assertThat(capture.afterCommit.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("신청 상태가 바뀌어 커밋되면 알리고, 롤백되면 알리지 않는다")
    void statusChangeIsAnnouncedOnlyWhenCommitted() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        capture.afterCommit.set(0);

        tx.executeWithoutResult(status -> requestRepository.findById(request.getRequestId()).orElseThrow()
                .markAsProcessing());
        assertThat(capture.afterCommit.get()).isEqualTo(1);

        tx.executeWithoutResult(status -> {
            requestRepository.findById(request.getRequestId()).orElseThrow().revertToPending();
            requestRepository.flush();
            status.setRollbackOnly();
        });
        assertThat(capture.afterCommit.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("포트 행이 저장돼도 알린다")
    void portRowIsAnnounced() {
        capture.afterCommit.set(0);

        portRequestRepository.save(PortRequests.builder()
                .internalPort(8888).usagePurpose("jupyter").request(request).resourceGroup(resourceGroup).build());

        assertThat(capture.afterCommit.get()).isEqualTo(1);
    }
}
