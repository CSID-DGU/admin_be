package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.containerImage.repository.ContainerImageRepository;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.JobResultResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestGroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.resourceGroups.entity.ResourceGroup;
import DGU_AI_LAB.admin_be.domain.resourceGroups.repository.ResourceGroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 승인 대기 그룹의 잠금·gid 반영·삭제를 실제 DB(H2)에서 검증한다. 테스트 메서드를 트랜잭션으로 감싸지 않고
 * (NOT_SUPPORTED) 단계마다 트랜잭션을 커밋해, 운영처럼 서로 다른 트랜잭션이 엇갈리는 상황을 만든다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(PendingGroupService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PendingGroupServiceIntegrationTest {

    @Autowired private PendingGroupService pendingGroupService;
    @Autowired private GroupRepository groupRepository;
    @Autowired private RequestGroupRepository requestGroupRepository;
    @Autowired private RequestRepository requestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ResourceGroupRepository resourceGroupRepository;
    @Autowired private ContainerImageRepository containerImageRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private TransactionTemplate tx;
    private User user;
    private ResourceGroup resourceGroup;
    private ContainerImage image;
    private final List<Long> requestIds = new ArrayList<>();
    private final List<Long> groupIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder()
                .email("pending-" + suffix + "@dgu.ac.kr").password("encoded").name("승인대기그룹")
                .studentId("S" + suffix).phone("010-0000-0000").department("컴퓨터공학과").build());
        resourceGroup = resourceGroupRepository.save(ResourceGroup.builder()
                .resourceGroupName("pending-" + suffix).description("테스트").serverName("farm").build());
        image = containerImageRepository.save(ContainerImage.builder()
                .imageName("img-" + suffix).imageVersion("1").cudaVersion("11.8").description("테스트").build());
    }

    @AfterEach
    void tearDown() {
        // 이 테스트가 만든 행만 지운다 — 같은 DB 를 쓰는 다른 테스트의 데이터는 건드리지 않는다.
        for (Long requestId : requestIds) {
            jdbcTemplate.update("DELETE FROM request_groups WHERE request_id = ?", requestId);
            jdbcTemplate.update("DELETE FROM requests WHERE request_id = ?", requestId);
        }
        for (Long groupId : groupIds) {
            jdbcTemplate.update("DELETE FROM request_groups WHERE group_id = ?", groupId);
            jdbcTemplate.update("DELETE FROM \"groups\" WHERE group_id = ?", groupId);
        }
        containerImageRepository.deleteById(image.getImageId());
        resourceGroupRepository.deleteById(resourceGroup.getRsgroupId());
        userRepository.deleteById(user.getUserId());
    }

    // ===== 픽스처 =====

    private Long group(String name, Long gid) {
        Long id = groupRepository.save(Group.builder()
                .groupName(name + "-" + UUID.randomUUID().toString().substring(0, 6)).ubuntuGid(gid).build()).getGroupId();
        groupIds.add(id);
        return id;
    }

    /** 이 그룹들을 고른 신청을 만든다. */
    private Long request(Long... chosenGroupIds) {
        Long id = tx.execute(status -> {
            Request req = requestRepository.saveAndFlush(Request.builder()
                    .expiresAt(LocalDateTime.now().plusDays(30)).usagePurpose("승인 대기 그룹 테스트").formAnswers("{}")
                    .user(userRepository.findById(user.getUserId()).orElseThrow())
                    .resourceGroup(resourceGroup).containerImage(image).build());
            for (Long groupId : chosenGroupIds) {
                req.addGroup(groupRepository.findById(groupId).orElseThrow());
            }
            return req.getRequestId();
        });
        requestIds.add(id);
        return id;
    }

    private void processing(Long requestId) {
        tx.executeWithoutResult(status -> requestRepository.findByIdForUpdate(requestId).orElseThrow().markAsProcessing());
    }

    private void reject(Long requestId) {
        tx.executeWithoutResult(status -> {
            Request req = requestRepository.findByIdForUpdate(requestId).orElseThrow();
            req.reject("거절");
            pendingGroupService.deleteAbandoned(req);
        });
    }

    private void cancel(Long requestId) {
        tx.executeWithoutResult(status -> {
            Request req = requestRepository.findByIdForUpdate(requestId).orElseThrow();
            req.delete();
            pendingGroupService.deleteAbandoned(req);
        });
    }

    private boolean exists(Long groupId) {
        return groupRepository.findById(groupId).isPresent();
    }

    private int requestGroupRows(Long requestId) {
        return requestGroupRepository.findAllByRequest_RequestId(requestId).size();
    }

    // ===== 테스트 =====

    @Test
    @DisplayName("gid 없는 그룹 두 개를 고른 신청은 둘 다 저장된다 — gid 로 비교하면 하나로 합쳐진다")
    void twoPendingGroupsAreBothKept() {
        Long a = group("vision", null);
        Long b = group("nlp", null);

        Long requestId = request(a, b);

        assertThat(requestGroupRows(requestId)).isEqualTo(2);
    }

    @Test
    @DisplayName("신청 목록의 고른 그룹을 한 번에 읽는다 — 신청마다 그룹이 맞게 붙는다")
    void requestedGroupsAreLoadedInOneQuery() {
        Long vision = group("vision", null);
        Long created = group("ailab", 70001L);
        Long first = request(vision, created);
        Long second = request(vision);
        Long none = request();

        var rows = tx.execute(status -> requestGroupRepository.findAllWithGroupByRequestIds(List.of(first, second, none))
                .stream().map(rg -> rg.getRequest().getRequestId() + ":" + rg.getGroup().getGroupId()).sorted().toList());

        assertThat(rows).containsExactlyInAnyOrder(first + ":" + vision, first + ":" + created, second + ":" + vision);
    }

    @Nested
    @DisplayName("고른 신청이 모두 끝나면 승인 대기 그룹을 지운다")
    class DeleteAbandoned {

        @Test
        @DisplayName("같은 그룹을 고른 신청이 남아 있으면 남기고, 마지막 신청이 거절되면 그룹과 그 신청 기록을 지운다")
        void deletesOnlyWhenTheLastRequestFinishes() {
            Long vision = group("vision", null);
            Long first = request(vision);
            Long second = request(vision);

            reject(first);
            assertThat(exists(vision)).isTrue();

            reject(second);
            assertThat(exists(vision)).isFalse();
            assertThat(requestGroupRows(first)).isZero();
            assertThat(requestGroupRows(second)).isZero();
            assertThat(requestRepository.findById(second).orElseThrow().getStatus()).isEqualTo(Status.DENIED);
        }

        @Test
        @DisplayName("취소로 끝나도 같다. 거절된 신청은 이미 끝난 것으로 본다")
        void cancelAlsoCounts() {
            Long vision = group("vision", null);
            Long rejected = request(vision);
            Long cancelled = request(vision);
            reject(rejected);

            cancel(cancelled);

            assertThat(exists(vision)).isFalse();
        }

        @Test
        @DisplayName("처리 중인 신청이 고르고 있으면 지우지 않는다")
        void keepsWhileAnotherRequestIsProcessing() {
            Long vision = group("vision", null);
            Long other = request(vision);
            processing(other);
            Long mine = request(vision);

            reject(mine);

            assertThat(exists(vision)).isTrue();
            assertThat(requestGroupRows(other)).isEqualTo(1);
        }

        @Test
        @DisplayName("gid 가 있는 그룹은 고른 신청이 없어도 지우지 않는다 — 인프라에 이미 있다")
        void neverDeletesCreatedGroups() {
            Long vision = group("vision", null);
            Long created = group("ailab", 70001L);
            Long requestId = request(vision, created);

            reject(requestId);

            assertThat(exists(vision)).isFalse();
            assertThat(exists(created)).isTrue();
            assertThat(requestGroupRows(requestId)).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 트랜잭션에서 그룹을 먼저 읽어 둔 사이 다른 트랜잭션이 gid 를 채웠어도 지우지 않는다")
        void staleReadDoesNotDeleteACreatedGroup() throws Exception {
            Long vision = group("vision", null);
            Long requestId = request(vision);

            CountDownLatch loaded = new CountDownLatch(1);
            CountDownLatch gidAssigned = new CountDownLatch(1);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<?> rejecting = executor.submit(() -> tx.executeWithoutResult(status -> {
                    Request req = requestRepository.findByIdForUpdate(requestId).orElseThrow();
                    // 그룹을 먼저 읽어 둔다 — 이 시점에는 gid 가 없다.
                    req.getRequestGroups().forEach(rg -> assertThat(rg.getGroup().getUbuntuGid()).isNull());
                    loaded.countDown();
                    await(gidAssigned);
                    req.reject("거절");
                    pendingGroupService.deleteAbandoned(req);
                }));
                await(loaded);
                // 다른 신청의 생성 작업이 성공해 gid 를 채우고 커밋했다.
                tx.executeWithoutResult(status ->
                        groupRepository.findAllByIdForUpdate(List.of(vision)).get(0).assignGid(70100L));
                gidAssigned.countDown();
                rejecting.get(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }

            assertThat(groupRepository.findById(vision).orElseThrow().getUbuntuGid()).isEqualTo(70100L);
            assertThat(requestGroupRows(requestId)).isEqualTo(1);
        }

        @Test
        @DisplayName("지우는 동안 같은 그룹으로 신청하려는 쪽은 기다렸다가 그룹이 없어진 것을 본다")
        void newRequestWaitsForDeletionAndSeesItGone() throws Exception {
            Long vision = group("vision", null);
            Long requestId = request(vision);

            CountDownLatch locked = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<?> rejecting = executor.submit(() -> tx.executeWithoutResult(status -> {
                    Request req = requestRepository.findByIdForUpdate(requestId).orElseThrow();
                    req.reject("거절");
                    pendingGroupService.deleteAbandoned(req);
                    locked.countDown();
                    sleep(300); // 그룹 행을 잠근 채 커밋을 미룬다
                }));
                await(locked);
                AtomicReference<Integer> seen = new AtomicReference<>();
                Future<?> submitting = executor.submit(() -> tx.executeWithoutResult(status ->
                        seen.set(groupRepository.findAllByIdForUpdate(List.of(vision)).size())));
                rejecting.get(10, TimeUnit.SECONDS);
                submitting.get(10, TimeUnit.SECONDS);

                assertThat(seen.get()).isZero();
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("승인 직전 잠금")
    class LockForApproval {

        @Test
        @DisplayName("다른 신청이 같은 승인 대기 그룹을 만드는 중이면 막는다")
        void rejectsWhileAnotherRequestCreatesTheSameGroup() {
            Long vision = group("vision", null);
            Long first = request(vision);
            processing(first);
            Long second = request(vision);

            assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                    pendingGroupService.lockForApproval(requestRepository.findByIdForUpdate(second).orElseThrow())))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PENDING_GROUP_IN_PROGRESS);
        }

        @Test
        @DisplayName("다른 신청이 대기 중이기만 하면 막지 않고, 그룹을 최신 상태로 돌려준다")
        void allowsWhenOthersArePending() {
            Long vision = group("vision", null);
            Long created = group("ailab", 70001L);
            request(vision);
            Long mine = request(vision, created);

            List<Group> groups = tx.execute(status ->
                    pendingGroupService.lockForApproval(requestRepository.findByIdForUpdate(mine).orElseThrow()));

            assertThat(groups).extracting(Group::getGroupId).containsExactlyInAnyOrder(vision, created);
            assertThat(groups).filteredOn(Group::isPending).extracting(Group::getGroupId).containsExactly(vision);
        }

        @Test
        @DisplayName("두 승인이 동시에 오면 뒤 승인은 앞 승인이 커밋할 때까지 기다렸다가 막힌다 — gid 를 두 번 발급하지 않는다")
        void concurrentApprovalsAreSerialized() throws Exception {
            Long vision = group("vision", null);
            Long first = request(vision);
            Long second = request(vision);

            CountDownLatch locked = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<?> approvingFirst = executor.submit(() -> tx.executeWithoutResult(status -> {
                    Request req = requestRepository.findByIdForUpdate(first).orElseThrow();
                    req.markAsProcessing();
                    pendingGroupService.lockForApproval(req);
                    locked.countDown();
                    sleep(300);
                }));
                await(locked);
                AtomicReference<Throwable> secondError = new AtomicReference<>();
                Future<?> approvingSecond = executor.submit(() -> {
                    try {
                        tx.executeWithoutResult(status -> {
                            Request req = requestRepository.findByIdForUpdate(second).orElseThrow();
                            req.markAsProcessing();
                            pendingGroupService.lockForApproval(req);
                        });
                    } catch (Throwable t) {
                        secondError.set(t);
                    }
                });
                approvingFirst.get(10, TimeUnit.SECONDS);
                approvingSecond.get(10, TimeUnit.SECONDS);

                assertThat(secondError.get()).isInstanceOf(BusinessException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.PENDING_GROUP_IN_PROGRESS);
                // 막힌 승인은 롤백돼 그대로 대기 중이다.
                assertThat(requestRepository.findById(second).orElseThrow().getStatus()).isEqualTo(Status.PENDING);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("생성 작업 결과의 gid 반영")
    class AssignFromResult {

        @Test
        @DisplayName("결과의 gid 로 승인 대기 그룹을 채운다. gid 가 있는 그룹은 건드리지 않는다")
        void fillsPendingGroups() {
            Long vision = group("vision", null);
            Long created = group("ailab", 70001L);
            Long requestId = request(vision, created);
            String visionName = groupRepository.findById(vision).orElseThrow().getGroupName();

            String problem = tx.execute(status -> pendingGroupService.assignFromResult(
                    requestRepository.findByIdForUpdate(requestId).orElseThrow(),
                    List.of(new JobResultResponseDTO.GroupResult(visionName, 70002L))));

            assertThat(problem).isNull();
            assertThat(groupRepository.findById(vision).orElseThrow().getUbuntuGid()).isEqualTo(70002L);
            assertThat(groupRepository.findById(created).orElseThrow().getUbuntuGid()).isEqualTo(70001L);
        }

        @Test
        @DisplayName("결과에 gid 가 없거나 다른 그룹이 이미 쓰는 gid 면 아무것도 바꾸지 않고 이유를 돌려준다")
        void reportsProblemsWithoutChangingAnything() {
            Long vision = group("vision", null);
            Long nlp = group("nlp", null);
            Long created = group("ailab", 70001L);
            Long requestId = request(vision, nlp);
            String visionName = groupRepository.findById(vision).orElseThrow().getGroupName();
            String nlpName = groupRepository.findById(nlp).orElseThrow().getGroupName();

            String missing = tx.execute(status -> pendingGroupService.assignFromResult(
                    requestRepository.findByIdForUpdate(requestId).orElseThrow(),
                    List.of(new JobResultResponseDTO.GroupResult(visionName, 70002L))));
            String conflict = tx.execute(status -> pendingGroupService.assignFromResult(
                    requestRepository.findByIdForUpdate(requestId).orElseThrow(),
                    List.of(new JobResultResponseDTO.GroupResult(visionName, 70002L),
                            new JobResultResponseDTO.GroupResult(nlpName, 70001L))));
            String noResult = tx.execute(status -> pendingGroupService.assignFromResult(
                    requestRepository.findByIdForUpdate(requestId).orElseThrow(), null));

            assertThat(missing).contains(nlpName);
            assertThat(conflict).contains(nlpName).contains("70001");
            assertThat(noResult).contains(visionName).contains(nlpName);
            assertThat(groupRepository.findById(vision).orElseThrow().getUbuntuGid()).isNull();
            assertThat(groupRepository.findById(nlp).orElseThrow().getUbuntuGid()).isNull();
            assertThat(groupRepository.findById(created).orElseThrow().getUbuntuGid()).isEqualTo(70001L);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
