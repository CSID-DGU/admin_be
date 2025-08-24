package DGU_AI_LAB.admin_be.domain.groups.controller;

import DGU_AI_LAB.admin_be.domain.groups.dto.request.CreateGroupRequestDTO;
import DGU_AI_LAB.admin_be.domain.groups.dto.response.GroupResponseDTO;
import DGU_AI_LAB.admin_be.domain.groups.service.GroupService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

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
     * 새로운 그룹을 생성하는 API
     * POST /api/groups
     */
    @PostMapping
    public ResponseEntity<SuccessResponse<?>> createGroup(@RequestBody @Valid CreateGroupRequestDTO dto) {
        log.info("[createGroup] 새로운 그룹 생성 요청 접수: {}", dto.groupName());
        GroupResponseDTO response = groupService.createGroup(dto);
        return SuccessResponse.created(response);
    }
}
