package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.usedIds.repository.UsedIdRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {

    private final GroupRepository groupRepository;
    private final UsedIdRepository usedIdRepository;

    /**
     * 모든 그룹 정보를 조회하는 API
     * GET /api/groups
     */
    public List<GroupResponseDTO> getAllGroups() {
        log.info("[getAllGroups] 모든 그룹 정보 조회 시작");
        var groups = groupRepository.findAll();

        if (groups.isEmpty()) {
            log.warn("[getAllGroups] 조회된 그룹 정보가 없습니다.");
            throw new BusinessException(ErrorCode.NO_AVAILABLE_GROUPS);
        }

        var response = groups.stream()
                .map(GroupResponseDTO::fromEntity)
                .toList();

        log.info("[getAllGroups] 모든 그룹 정보 조회 완료. {}개 그룹", response.size());
        return response;
    }

    /**
     * 새로운 그룹을 생성하는 API
     * POST /api/groups
     */
    @Transactional
    public GroupResponseDTO createGroup(CreateGroupRequestDTO dto) {
        log.info("[createGroup] 그룹 생성 요청 시작: groupName={}, ubuntuGid={}", dto.groupName(), dto.ubuntuGid());

        // 1. 요청받은 GID로 UsedId가 이미 존재하는지 확인하고, 없으면 생성
        Optional<UsedId> existingUsedId = usedIdRepository.findById(dto.ubuntuGid());
        UsedId usedId;
        if (existingUsedId.isPresent()) {
            log.warn("[createGroup] 중복된 GID가 UsedId에 이미 존재합니다: {}", dto.ubuntuGid());
            usedId = existingUsedId.get();
        } else {
            usedId = usedIdRepository.saveAndFlush(UsedId.builder().idValue(dto.ubuntuGid()).build());
            log.info("[createGroup] UsedId에 GID {} 할당 완료", dto.ubuntuGid());
        }

        // 2. 해당 GID의 그룹이 이미 존재하는지 확인
        if (groupRepository.existsByUbuntuGid(dto.ubuntuGid())) {
            log.warn("[createGroup] 중복된 GID를 가진 그룹이 이미 존재합니다: {}", dto.ubuntuGid());
            throw new BusinessException(ErrorCode.DUPLICATE_GROUP_ID);
        }

        // 3. 그룹 엔티티 생성 및 저장
        Group group = Group.builder()
                .groupName(dto.groupName())
                .ubuntuGid(dto.ubuntuGid())
                .usedId(usedId)
                .build();

        group = groupRepository.save(group);
        log.info("[createGroup] 그룹 생성 완료: id={}, name={}", group.getGroupId(), group.getGroupName());

        return GroupResponseDTO.fromEntity(group);
    }
}