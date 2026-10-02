package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperationKind;
import DGU_AI_LAB.admin_be.domain.groups.entity.GroupOperationStatus;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupOperationRepository;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.users.repository.UserRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.validation.ReservedLinuxNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 공용 그룹 조회와 생성. 멤버 추가·제거는 {@link GroupOperationService}가 작업으로 처리한다.
 *
 * <p>생성은 DB 에만 한다(gid 없음 — 승인 대기 그룹). 인프라 그룹은 이 그룹을 고른 신청이 승인될 때 생성 작업이
 * 만들고, 그 결과의 gid 를 {@link PendingGroupService}가 채운다. 관리자가 승인하기 전에는 인프라 자원도 gid 도
 * 쓰지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupOperationRepository operationRepository;
    private final UserRepository userRepository;
    private final ReservedLinuxNames reservedLinuxNames;
    private final GroupCreateThrottle groupCreateThrottle;

    /**
     * 모든 그룹 정보를 조회하는 API
     * GET /api/groups
     */
    @Transactional(readOnly = true)
    public List<GroupResponseDTO> getAllGroups() {
        log.info("[getAllGroups] 모든 그룹 정보 조회 시작");
        var groups = groupRepository.findAll();

        var response = groups.stream()
                .map(GroupResponseDTO::fromEntity)
                .toList();

        log.info("[getAllGroups] 모든 그룹 정보 조회 완료. {}개 그룹", response.size());
        return response;
    }

    /**
     * 승인 대기 그룹을 만든다. 인프라에는 아직 만들지 않으므로 이름이 나중에 승인 단계에서 거절되지 않도록, 그때
     * config-server 가 막을 이름(계정명과 같은 이름, 이미지·시스템 예약 이름)을 여기서 미리 막는다.
     *
     * @throws BusinessException 이름이 이미 쓰이거나(409), 계정명과 같거나(400), 예약 이름이거나(409),
     *                           같은 이름을 예전 방식으로 만드는 중이거나(409), 하루 한도를 넘은 경우(429)
     */
    @Transactional
    public GroupResponseDTO createGroup(CreateGroupRequestDTO dto, Long userId) {
        String name = dto.groupName();
        if (groupRepository.existsByGroupName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_GROUP_NAME);
        }
        // AD 에서 사용자와 그룹은 이름 공간을 같이 쓴다. 계정명만 등록하고 아직 승인 전인 사용자도 막아야
        // 그 사용자의 계정 생성이 나중에 실패하지 않는다.
        if (userRepository.existsByUbuntuUsername(name)) {
            throw new BusinessException(ErrorCode.GROUP_NAME_CONFLICTS_USER);
        }
        if (reservedLinuxNames.contains(name)) {
            throw new BusinessException(ErrorCode.RESERVED_GROUP_NAME);
        }
        // 이 변경 전에 등록된 생성 작업이 아직 돌고 있으면, 끝날 때 같은 이름의 그룹을 저장한다.
        if (operationRepository.existsByKindAndGroupNameAndStatus(
                GroupOperationKind.CREATE, name, GroupOperationStatus.PROCESSING)) {
            throw new BusinessException(ErrorCode.GROUP_OPERATION_IN_PROGRESS);
        }
        // 검증을 통과한 요청만 센다 — 이름 중복 같은 실패로 한도를 쓰지 않게 한다.
        groupCreateThrottle.acquire(userId);

        Group group;
        try {
            group = groupRepository.saveAndFlush(Group.builder().groupName(name).build());
        } catch (DataIntegrityViolationException e) {
            // 같은 이름을 동시에 만든 다른 요청이 먼저 저장했다.
            throw new BusinessException(ErrorCode.DUPLICATE_GROUP_NAME);
        }
        log.info("[createGroup] 승인 대기 그룹 생성: groupId={}, groupName={}, userId={}", group.getGroupId(), name, userId);
        return GroupResponseDTO.fromEntity(group);
    }
}
