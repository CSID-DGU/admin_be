package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupLeaveRequest;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupLeaveStatus;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupLeaveRequestRepository;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 같은 대기 신청에 승인과 거절이 동시에 들어와도 한쪽만 결정된다. 각 호출이 자기 트랜잭션을 쓰도록 테스트 트랜잭션을
 * 끄고, 픽스처는 커밋한 뒤 정리한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(GroupLeaveService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GroupLeaveDecisionRaceIntegrationTest {

    @Autowired private GroupLeaveService service;
    @Autowired private GroupLeaveRequestRepository leaveRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private UserRepository userRepository;
    @MockitoBean private GroupOperationService groupOperationService;

    @Test
    @DisplayName("승인과 거절이 동시에 와도 하나만 결정되고, 제거 작업은 승인이 이겼을 때만 등록된다")
    void concurrentApproveAndRejectDecideOnce() throws Exception {
        Group group = groupRepository.save(Group.builder().groupName("race-lab").ubuntuGid(2900L).build());
        User member = userRepository.save(User.builder().email("race-member@dgu.ac.kr").password("p").name("이름")
                .studentId("2099001111").phone("010-1111-2222").department("컴퓨터공학과").build());
        User admin = userRepository.save(User.builder().email("race-admin@dgu.ac.kr").password("p").name("관리")
                .studentId("2099002222").phone("010-1111-3333").department("컴퓨터공학과").build());
        member.addGroupIfAbsent(group);
        userRepository.save(member);
        Long leaveId = service.request(member.getUserId(), group.getGroupId()).getGroupLeaveRequestId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<GroupLeaveRequest> approve = pool.submit(() -> {
                start.await();
                return service.approve(leaveId, admin.getUserId());
            });
            Future<GroupLeaveRequest> reject = pool.submit(() -> {
                start.await();
                return service.reject(leaveId, admin.getUserId());
            });
            start.countDown();

            List<GroupLeaveStatus> outcomes = new ArrayList<>();
            int failures = 0;
            for (Future<GroupLeaveRequest> f : List.of(approve, reject)) {
                try {
                    outcomes.add(f.get().getStatus());
                } catch (java.util.concurrent.ExecutionException e) {
                    failures++;
                    assertThat(e.getCause()).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) e.getCause()).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);
                }
            }
            assertThat(outcomes).hasSize(1);
            assertThat(failures).isEqualTo(1);

            GroupLeaveStatus finalStatus = leaveRepository.findById(leaveId).orElseThrow().getStatus();
            assertThat(finalStatus).isEqualTo(outcomes.get(0));
            verify(groupOperationService, times(finalStatus == GroupLeaveStatus.APPROVED ? 1 : 0))
                    .requestRemove(member.getUserId(), group.getGroupId(), admin.getUserId());
        } finally {
            pool.shutdownNow();
            leaveRepository.deleteAll();
            userRepository.deleteAll();
            groupRepository.deleteAll();
        }
    }
}
