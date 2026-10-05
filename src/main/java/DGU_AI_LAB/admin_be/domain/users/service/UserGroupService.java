package DGU_AI_LAB.admin_be.domain.users.service;

import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupOperationResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupOperationService;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.entity.UserGroup;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 관리자가 계정의 공용 그룹 멤버십을 보고 빼는 경로. 빼는 일은 작업으로 등록되고 GroupOperationService가 결과를 반영한다. 추가는 신청 승인(AdminRequestCommandService)과 변경 요청 승인(AdminModificationCommandService)이 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final UserRepository userRepository;
    private final GroupOperationService groupOperationService;

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
     * 계정을 공용 그룹에서 빼는 작업을 등록한다. 권한의 원천인 AD 를 먼저 바꾸고, 그 작업이 성공한 뒤에만 DB 를
     * 맞춘다 — DB 가 AD 보다 앞서면 화면에는 빠졌다고 나오는데 실제로는 그 그룹에 공유된 폴더가 계속 열린다.
     */
    public GroupOperationResponseDTO removeUserFromGroup(Long userId, Long groupId, Long adminId) {
        return groupOperationService.requestRemove(userId, groupId, adminId);
    }
}
