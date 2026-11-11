package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupLeaveResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupLeaveRequest;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupLeaveStatus;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupLeaveRequestRepository;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 탈퇴 신청 → 승인/거절 흐름을 실제 DB 표(H2)와 실제 저장소로 끝까지 따라간다. 제거 작업 등록만 대역을 쓴다. */
@DataJpaTest
@ActiveProfiles("test")
@Import(GroupLeaveService.class)
class GroupLeaveServiceIntegrationTest {

    @Autowired private GroupLeaveService service;
    @Autowired private GroupLeaveRequestRepository leaveRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TestEntityManager em;
    @MockitoBean private GroupOperationService groupOperationService;

    private User member;
    private User admin;
    private Group group;

    @BeforeEach
    void setUp() {
        group = groupRepository.save(Group.builder().groupName("vision-lab").ubuntuGid(2000L).build());
        member = userRepository.save(user("member@dgu.ac.kr", "2021001111"));
        admin = userRepository.save(user("admin@dgu.ac.kr", "2021002222"));
        member.addGroupIfAbsent(group);
        em.flush();
        em.clear();
    }

    private User user(String email, String studentId) {
        return User.builder().email(email).password("p").name("이름").studentId(studentId)
                .phone("010-1111-2222").department("컴퓨터공학과").build();
    }

    @Test
    @DisplayName("신청 → 대기 목록 → 승인: 상태가 APPROVED로 저장되고 제거 작업이 등록된다")
    void requestThenApprove() {
        GroupLeaveRequest requested = service.request(member.getUserId(), group.getGroupId());
        em.flush();
        em.clear();

        assertThat(service.pending()).extracting(GroupLeaveRequest::getGroupLeaveRequestId)
                .containsExactly(requested.getGroupLeaveRequestId());
        assertThat(service.mine(member.getUserId())).hasSize(1);

        GroupLeaveRequest approved = service.approve(requested.getGroupLeaveRequestId(), admin.getUserId());
        em.flush();
        em.clear();
        // 컨트롤러는 트랜잭션 밖에서 응답을 만든다 — 지연 로딩 프록시가 남아 있으면 여기서 터진다.
        assertThat(GroupLeaveResponseDTO.fromEntity(approved).groupName()).isEqualTo("vision-lab");

        GroupLeaveRequest saved = leaveRepository.findById(requested.getGroupLeaveRequestId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(GroupLeaveStatus.APPROVED);
        assertThat(saved.getDecidedAt()).isNotNull();
        assertThat(service.pending()).isEmpty();
        verify(groupOperationService).requestRemove(member.getUserId(), group.getGroupId(), admin.getUserId());
    }

    @Test
    @DisplayName("거절하면 REJECTED로 저장되고 제거 작업은 등록되지 않는다")
    void reject() {
        GroupLeaveRequest requested = service.request(member.getUserId(), group.getGroupId());
        GroupLeaveRequest rejected = service.reject(requested.getGroupLeaveRequestId(), admin.getUserId());
        em.flush();
        em.clear();
        assertThat(GroupLeaveResponseDTO.fromEntity(rejected).userName()).isEqualTo("이름");

        assertThat(leaveRepository.findById(requested.getGroupLeaveRequestId()).orElseThrow().getStatus())
                .isEqualTo(GroupLeaveStatus.REJECTED);
        verifyNoInteractions(groupOperationService);
    }

    @Test
    @DisplayName("같은 그룹을 대기 중에 다시 신청하거나, 이미 결정된 신청을 다시 결정하면 거부된다")
    void duplicateAndDecidedAreRejected() {
        GroupLeaveRequest requested = service.request(member.getUserId(), group.getGroupId());
        em.flush();

        assertThatThrownBy(() -> service.request(member.getUserId(), group.getGroupId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.GROUP_LEAVE_ALREADY_REQUESTED);

        service.reject(requested.getGroupLeaveRequestId(), admin.getUserId());
        assertThatThrownBy(() -> service.approve(requested.getGroupLeaveRequestId(), admin.getUserId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST_STATUS);
    }

    @Test
    @DisplayName("속하지 않은 그룹은 신청할 수 없다")
    void nonMemberRejected() {
        Group other = groupRepository.save(Group.builder().groupName("other").ubuntuGid(2001L).build());

        assertThatThrownBy(() -> service.request(member.getUserId(), other.getGroupId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.GROUP_LEAVE_NOT_MEMBER);
        assertThat(service.mine(member.getUserId())).isEqualTo(List.of());
    }
}
