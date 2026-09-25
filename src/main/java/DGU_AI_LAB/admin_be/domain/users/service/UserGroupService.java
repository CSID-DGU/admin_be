package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.entity.UserGroup;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;

/**
 * 관리자가 계정의 공용 그룹 멤버십을 보고 빼는 경로. 추가는 신청 승인(AdminRequestCommandService)과 변경 요청 승인(AdminModificationCommandService)이 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final RequestRepository requestRepository;
    private final GroupService groupService;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public List<GroupResponseDTO> getGroupsOfUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
        return user.getUserGroups().stream()
                .map(UserGroup::getGroup)
                .sorted(Comparator.comparing(Group::getGroupName))
                .map(GroupResponseDTO::fromEntity)
                .toList();
    }

    /**
     * 계정을 공용 그룹에서 뺀다. 순서는 추가와 같다 — 권한의 원천인 AD를 먼저 바꾸고, 성공한 뒤에만
     * DB를 맞춘다. DB가 AD보다 앞서면 화면에는 빠졌다고 나오는데 실제로는 팀 디렉터리가 계속 열린다.
     *
     * config-server 호출이 멱등이라, AD 반영 후 DB 커밋이 실패해도 같은 요청을 다시 보내면 끝난다.
     * 팀 디렉터리와 그 안의 파일은 건드리지 않는다 — 파일의 그룹은 팀 그대로라 남은 팀원이 계속 쓴다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void removeUserFromGroup(Long userId, Long groupId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Target target = tx.execute(status -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
            Group group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.GROUP_NOT_FOUND));
            return new Target(user.getUbuntuUsername(), group.getGroupName());
        });

        // 우분투 계정을 한 번도 만들지 않은 사용자는 AD에 반영된 멤버십이 없다 — DB만 정리한다.
        if (target.username() != null) {
            groupService.removeUserFromGroup(target.username(), target.groupName());
            groupService.triggerNasGssFlush(target.username());
        }

        try {
            tx.executeWithoutResult(status -> {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
                user.removeGroup(groupId);
                // 다시 만들 수 있는 컨테이너만 본다. 대기 중인 신청은 아직 승인 판단 전이라 그대로 둔다.
                for (Request request : requestRepository.findAllByUser_UserIdAndStatusIn(userId, Status.activeStatuses())) {
                    request.removeGroup(groupId);
                }
            });
        } catch (RuntimeException e) {
            log.error("[removeUserFromGroup] AD에서는 뺐으나 DB 반영 실패 — 같은 요청을 다시 보내면 이어서 끝난다: " +
                    "userId={}, groupId={}", userId, groupId, e);
            throw e;
        }
        log.info("[removeUserFromGroup] 그룹 제거 완료: userId={}, username={}, group={}",
                userId, target.username(), target.groupName());
    }

    private record Target(String username, String groupName) {}
}
