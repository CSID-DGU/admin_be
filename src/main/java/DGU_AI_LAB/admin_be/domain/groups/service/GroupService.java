package DGU_AI_LAB.admin_be.domain.groups.service;

import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GroupService {

    private final GroupRepository groupRepository;
    private final RequestRepository requestRepository;
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

        // 1. ubuntuUsername이 제공된 경우에만 유효성 검사 (필수 X)
        if (StringUtils.hasText(dto.ubuntuUsername())) {
            if (!requestRepository.existsByUbuntuUsernameAndUser_UserId(dto.ubuntuUsername(), userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN_REQUEST);
            }
        }

        // 2. DB에서 그룹명 중복을 먼저 확인합니다.
        if (groupRepository.existsByGroupName(dto.groupName())) {
            log.warn("[createGroup] DB에 이미 존재하는 그룹명입니다: {}", dto.groupName());
            throw new BusinessException(ErrorCode.DUPLICATE_GROUP_NAME);
        }

        // 3. 외부 API 호출을 위한 ubuntuUser 멤버 리스트를 구성합니다.
        List<String> members = Optional.ofNullable(dto.ubuntuUsername())
                .filter(StringUtils::hasText)
                .map(List::of)
                .orElse(Collections.emptyList());

        ConfigServerGroupRequest apiDto = new ConfigServerGroupRequest(
                dto.groupName(),
                members
        );

        ConfigServerGroupResponse apiResponse;
        try {
            log.info("[createGroup] 외부 그룹 생성 API 호출 시작: {}", apiDto);

            apiResponse = groupCreationWebClient
                    .put()
                    .uri("/accounts/groups")
                    .bodyValue(apiDto)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(body -> {
                                log.error("[createGroup] 외부 API 4xx 오류: 상태 코드={}, 응답={}", clientResponse.statusCode(), body);
                                if (clientResponse.statusCode() == HttpStatus.BAD_REQUEST) {
                                    if (body.contains("invalid members")) {
                                        return Mono.error(new BusinessException(ErrorCode.INVALID_GROUP_MEMBER));
                                    }
                                    return Mono.error(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));
                                } else if (clientResponse.statusCode() == HttpStatus.CONFLICT) {
                                    if (body.contains("group already exists")) {
                                        return Mono.error(new BusinessException(ErrorCode.DUPLICATE_GROUP_NAME));
                                    }
                                    return Mono.error(new BusinessException(ErrorCode.DUPLICATE_GROUP_ID));
                                }
                                return Mono.error(new BusinessException(ErrorCode.GROUP_CREATION_FAILED));
                            })
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(body -> {
                                log.error("[createGroup] 외부 API 5xx 오류: 상태 코드={}, 응답={}", clientResponse.statusCode(), body);
                                return Mono.error(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));
                            })
                    )
                    .bodyToMono(ConfigServerGroupResponse.class)
                    .block();

            log.info("[createGroup] 외부 API 호출 성공: {}", apiResponse);

        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            log.error("[createGroup] 외부 API 호출 실패: 상태 코드={}, 응답={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.GROUP_CREATION_FAILED);
        } catch (Exception e) {
            log.error("[createGroup] 외부 API 응답 파싱 또는 통신 오류 발생 — " +
                    "외부 시스템에 그룹이 생성되었을 수 있습니다. 수동 확인이 필요합니다. groupName={}", dto.groupName(), e);
            throw new BusinessException(ErrorCode.GROUP_CREATION_FAILED);
        }

        Long assignedGid = Optional.ofNullable(apiResponse)
                .map(ConfigServerGroupResponse::group)
                .map(ConfigServerGroupInfo::gid)
                .orElseThrow(() -> new BusinessException(ErrorCode.GID_ALLOCATION_FAILED));

        if (assignedGid <= 0) {
            log.error("[createGroup] 외부 API에서 유효하지 않은 GID를 반환했습니다: {}", assignedGid);
            throw new BusinessException(ErrorCode.GID_ALLOCATION_FAILED);
        }

        if (groupRepository.existsByUbuntuGid(assignedGid)) {
            log.warn("[createGroup] DB에 이미 존재하는 GID입니다: {}", assignedGid);
            throw new BusinessException(ErrorCode.DUPLICATE_GROUP_ID);
        }

        // 4. API 호출이 성공한 후에만 로컬 DB에 그룹을 저장합니다.
        Group group = Group.builder()
                .groupName(dto.groupName())
                .ubuntuGid(assignedGid)
                .build();

        group = groupRepository.save(group);
        log.info("[createGroup] 그룹 생성 및 로컬 DB 저장 완료: id={}, name={}", group.getGroupId(), group.getGroupName());

        return GroupResponseDTO.fromEntity(group);
    }

    // GroupService에서만 사용하는 infra API 요청/응답 DTO입니다. 테스트 검증을 위해 패키지 범위로 둡니다.
    record ConfigServerGroupRequest(
            String name,
            List<String> members
    ) {}

    record ConfigServerGroupResponse(
            ConfigServerGroupInfo group
    ) {}

    record ConfigServerGroupInfo(
            String name,
            @JsonProperty("gid")
            @JsonAlias({"ubuntuGid", "ubuntu_gid"})
            Long gid
    ) {}
}
