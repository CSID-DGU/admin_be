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
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 그룹 탈퇴 신청 → 관리자 승인. 승인하면 관리자가 직접 그룹에서 뺄 때와 같은 제거 작업({@link GroupOperationService#requestRemove})을
 * 등록한다. 신청만으로는 소속이 바뀌지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupLeaveService {

    private final GroupLeaveRequestRepository leaveRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupOperationService groupOperationService;

    @Transactional
    public GroupLeaveRequest request(Long userId, Long groupId) {
        // 같은 사용자의 동시 신청이 중복 대기 신청을 만들지 못하게 사용자 행을 잠근다.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.GROUP_NOT_FOUND));
        boolean member = user.getUserGroups().stream().anyMatch(ug -> ug.getGroup().getGroupId().equals(groupId));
        if (!member) {
            throw new BusinessException(ErrorCode.GROUP_LEAVE_NOT_MEMBER);
        }
        // 기본 그룹(계정 이름과 같은 그룹)은 어차피 제거할 수 없다 — 승인까지 기다리게 하지 않고 접수에서 거절한다.
        if (group.getGroupName().equals(user.getUbuntuUsername())) {
            throw new BusinessException(ErrorCode.PRIMARY_GROUP_REMOVAL);
        }
        if (leaveRepository.existsByUser_UserIdAndGroup_GroupIdAndStatus(userId, groupId, GroupLeaveStatus.PENDING)) {
            throw new BusinessException(ErrorCode.GROUP_LEAVE_ALREADY_REQUESTED);
        }
        GroupLeaveRequest saved = leaveRepository.save(GroupLeaveRequest.pending(user, group));
        log.info("[groupLeave] 탈퇴 신청: userId={}, groupId={}", userId, groupId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<GroupLeaveRequest> mine(Long userId) {
        return leaveRepository.findAllByUserWithGroup(userId);
    }

    @Transactional(readOnly = true)
    public List<GroupLeaveRequest> pending() {
        return leaveRepository.findAllByStatusWithUserAndGroup(GroupLeaveStatus.PENDING);
    }

    /**
     * 승인하고 제거 작업을 등록한다. 신청 행을 잠근 채 확인(대기) → 제거 작업 등록 → 승인 기록을 한 트랜잭션에서 한다.
     * 등록이 실패하면 아무것도 바뀌지 않고 신청은 대기로 남는다. 등록 뒤 커밋이 실패하면 작업 기록(GroupOperation)도 함께
     * 롤백돼 config-server 쪽 작업만 남고 폴러가 결과를 반영하지 못한다. 재승인하면 같은 제거 작업을 한 번 더 등록하므로
     * 제거가 멱등이어야 한다(config-server 확인 필요, 미확인).
     */
    @Transactional
    public GroupLeaveRequest approve(Long leaveRequestId, Long adminId) {
        GroupLeaveRequest request = lockPending(leaveRequestId);
        groupOperationService.requestRemove(request.getUser().getUserId(), request.getGroup().getGroupId(), adminId);
        request.approve(userRepository.getReferenceById(adminId));
        log.info("[groupLeave] 탈퇴 승인: leaveRequestId={}, adminId={}", leaveRequestId, adminId);
        return request;
    }

    @Transactional
    public GroupLeaveRequest reject(Long leaveRequestId, Long adminId) {
        GroupLeaveRequest request = lockPending(leaveRequestId);
        request.reject(userRepository.getReferenceById(adminId));
        log.info("[groupLeave] 탈퇴 거절: leaveRequestId={}, adminId={}", leaveRequestId, adminId);
        return request;
    }

    private GroupLeaveRequest lockPending(Long leaveRequestId) {
        GroupLeaveRequest request = leaveRepository.findByIdForUpdate(leaveRequestId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.GROUP_LEAVE_REQUEST_NOT_FOUND));
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_STATUS);
        }
        // 컨트롤러가 트랜잭션 밖에서 이름을 읽으므로(open-in-view 꺼짐) 지금 불러 둔다.
        Hibernate.initialize(request.getUser());
        Hibernate.initialize(request.getGroup());
        return request;
    }
}
