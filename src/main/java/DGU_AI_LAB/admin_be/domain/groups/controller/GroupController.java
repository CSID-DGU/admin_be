package DGU_AI_LAB.admin_be.domain.groups.controller;

import DGU_AI_LAB.admin_be.domain.groups.controller.docs.GroupApi;
import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupOperationService;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupService;
import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups")
public class GroupController implements GroupApi {

    private final GroupService groupService;
    private final GroupOperationService groupOperationService;

    /**
     * 모든 그룹 정보 조회 API
     * GET /api/groups
     * 그룹 ID와 그룹명을 반환합니다.
     */
    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getGroups() {
        List<GroupResponseDTO> groups = groupService.getAllGroups();
        return SuccessResponse.ok(groups);
    }

    /**
     * 그룹을 만드는 작업을 등록하는 API
     * POST /api/groups
     * 그룹은 작업이 끝나야 생긴다 — 응답의 작업 번호로 GET /api/groups/operations/{operationId}를 조회한다.
     */
    @PostMapping
    public ResponseEntity<SuccessResponse<?>> createGroup(
            @RequestBody @Valid CreateGroupRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        log.info("[createGroup] 새로운 그룹 생성 요청 접수: groupName={}", dto.groupName());
        return SuccessResponse.accepted(groupOperationService.requestCreate(dto, principal.getUserId()));
    }

    /**
     * 그룹 작업의 진행 상태 조회 API
     * GET /api/groups/operations/{operationId}
     */
    @GetMapping("/operations/{operationId}")
    public ResponseEntity<SuccessResponse<?>> getGroupOperation(
            @PathVariable Long operationId,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        boolean admin = principal.getRole() == Role.ADMIN;
        return SuccessResponse.ok(groupOperationService.get(operationId, principal.getUserId(), admin));
    }
}
