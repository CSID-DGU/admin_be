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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 같은 대기 신청에 승인과 거절이 동시에 들어와도 한쪽만 결정된다. 경합 시점을 mock 안에서 고정해 매번 같은 순서로 재현한다. 각 호출이 자기 트랜잭션을 쓰도록 테스트 트랜잭션을
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
    @DisplayName("승인이 제거 작업을 등록하는 동안 들어온 거절은 막혔다가 실패하고, 승인만 결정된다")
    void rejectDuringApproveRegistrationIsBlockedAndFails() throws Exception {
        Group group = groupRepository.save(Group.builder().groupName("race-lab").ubuntuGid(2900L).build());
        User member = userRepository.save(User.builder().email("race-member@dgu.ac.kr").password("p").name("이름")
                .studentId("2099001111").phone("010-1111-2222").department("컴퓨터공학과").build());
        User admin = userRepository.save(User.builder().email("race-admin@dgu.ac.kr").password("p").name("관리")
                .studentId("2099002222").phone("010-1111-3333").department("컴퓨터공학과").build());
        member.addGroupIfAbsent(group);
        userRepository.save(member);
        Long leaveId = service.request(member.getUserId(), group.getGroupId()).getGroupLeaveRequestId();

        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<Future<GroupLeaveRequest>> reject = new AtomicReference<>();
        // 승인이 제거 작업을 등록하는 순간에 거절을 끼워 넣는다. 잠금이 있으면 거절은 승인이 커밋할 때까지 막히고,
        // 잠금이 없으면(확인→등록→기록이 따로 놀면) 거절이 먼저 결정해 버린다. H2 잠금 대기 한도(2초)보다 짧게 기다린다.
        doAnswer(inv -> {
            reject.set(pool.submit(() -> service.reject(leaveId, admin.getUserId())));
            try {
                reject.get().get(500, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | ExecutionException blockedOrFailed) {
                // 막혔으면 정상. 결과 판정은 아래에서 한다.
            }
            return null;
        }).when(groupOperationService).requestRemove(member.getUserId(), group.getGroupId(), admin.getUserId());

        try {
            assertThat(service.approve(leaveId, admin.getUserId()).getStatus()).isEqualTo(GroupLeaveStatus.APPROVED);

            assertThatThrownBy(() -> reject.get().get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST_STATUS));
            assertThat(leaveRepository.findById(leaveId).orElseThrow().getStatus()).isEqualTo(GroupLeaveStatus.APPROVED);
            verify(groupOperationService, times(1))
                    .requestRemove(member.getUserId(), group.getGroupId(), admin.getUserId());
        } finally {
            pool.shutdownNow();
            leaveRepository.deleteAll();
            userRepository.deleteAll();
            groupRepository.deleteAll();
        }
    }
}
