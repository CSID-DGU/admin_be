package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.entity.UsedId;
import DGU_AI_LAB.admin_be.domain.usedIds.repository.UsedIdRepository;
import DGU_AI_LAB.admin_be.domain.usedIds.service.IdAllocationService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GroupService {

    private final GroupRepository groupRepository;
    private final UsedIdRepository usedIdRepository;
    private final RequestRepository requestRepository;
    private final ObjectMapper objectMapper;
    private final IdAllocationService idAllocationService;
    private final @Qualifier("configWebClient") WebClient groupCreationWebClient;


    /**
     * 모든 그룹 정보를 조회하는 API
     * GET /api/groups
     */
    @Transactional(readOnly = true)
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
    public GroupResponseDTO createGroup(CreateGroupRequestDTO dto, Long userId) {

        log.info("[createGroup] 그룹 생성 요청 시작: groupName={}, ubuntuUsername={}", dto.groupName(), dto.ubuntuUsername());

        // 1. 요청된 ubuntuUsername이 로그인한 사용자의 계정인지 확인하는 유효성 검사
        if (!requestRepository.existsByUbuntuUsernameAndUser_UserId(dto.ubuntuUsername(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_REQUEST);
        }

        // 2. DB에서 그룹명 중복을 먼저 확인합니다.
        if (groupRepository.existsByGroupName(dto.groupName())) {
            log.warn("[createGroup] DB에 이미 존재하는 그룹명입니다: {}", dto.groupName());
            throw new BusinessException(ErrorCode.DUPLICATE_GROUP_NAME);
        }

        // 3. 새로운 GID를 할당받습니다.
        Long assignedGid = idAllocationService.allocateNewGid();
        log.info("[createGroup] 새로운 GID 할당 완료: {}", assignedGid);

        // 4. GID가 int 타입으로 변환 가능한지 확인 (오버플로우 방지)
        if (assignedGid > Integer.MAX_VALUE || assignedGid < Integer.MIN_VALUE) {
            log.error("[createGroup] 할당된 GID가 int 범위를 벗어납니다: {}", assignedGid);
            throw new BusinessException(ErrorCode.GID_ALLOCATION_FAILED);
        }


        // 5. 외부 API 호출을 위한 DTO를 준비하고 호출합니다.
        Map<String, Object> apiDto = Map.of(
                "name", dto.groupName(),
                "gid", assignedGid.intValue(),
                "members", List.of(dto.ubuntuUsername())
        );

        try {
            log.info("[createGroup] 외부 그룹 생성 API 호출 시작: name={}, gid={}, members={}", apiDto.get("name"), apiDto.get("gid"), apiDto.get("members"));

            groupCreationWebClient.post()
                    .uri("/accounts/addgroup")
                    .bodyValue(apiDto)
                    .retrieve()
                    .onStatus(HttpStatus.BAD_REQUEST::equals, clientResponse ->
                            Mono.error(new BusinessException(ErrorCode.GROUP_CREATION_FAILED))
                    )
                    .onStatus(HttpStatus.CONFLICT::equals, clientResponse ->
                            Mono.error(new BusinessException(ErrorCode.DUPLICATE_GROUP_ID))
                    )
                    .onStatus(HttpStatus.INTERNAL_SERVER_ERROR::equals, clientResponse ->
                            Mono.error(new BusinessException(ErrorCode.EXTERNAL_API_ERROR))
                    )
                    .bodyToMono(Map.class)
                    .block();

            log.info("[createGroup] 외부 API 호출 성공");

        } catch (WebClientResponseException e) {
            log.error("[createGroup] 외부 API 호출 실패: 상태 코드={}, 응답={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.GROUP_CREATION_FAILED);
        } catch (Exception e) {
            log.error("[createGroup] 외부 API 호출 중 예상치 못한 오류 발생", e);
            throw new BusinessException(ErrorCode.GROUP_CREATION_FAILED);
        }

        // 4. API 호출이 성공한 후에만 로컬 DB에 그룹을 저장합니다.
        UsedId usedId = usedIdRepository.findById(assignedGid)
                .orElseGet(() -> usedIdRepository.saveAndFlush(UsedId.builder().idValue(assignedGid).build()));

        Group group = Group.builder()
                .groupName(dto.groupName())
                .ubuntuGid(assignedGid)
                .usedId(usedId)
                .build();

        group = groupRepository.save(group);
        log.info("[createGroup] 그룹 생성 및 로컬 DB 저장 완료: id={}, name={}", group.getGroupId(), group.getGroupName());

        return GroupResponseDTO.fromEntity(group);
    }
}