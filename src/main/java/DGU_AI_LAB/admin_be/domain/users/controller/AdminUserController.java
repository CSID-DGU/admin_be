package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserActivationRequestDTO;

import DGU_AI_LAB.admin_be.domain.users.controller.docs.AdminUserApi;
import DGU_AI_LAB.admin_be.domain.users.dto.request.ChangeRoleRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.service.AdminUserService;
import DGU_AI_LAB.admin_be.domain.users.service.UserGroupService;
import DGU_AI_LAB.admin_be.domain.users.service.UserService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class AdminUserController implements AdminUserApi {

    private final AdminUserService adminUserService;
    private final UserService userService;
    private final UserGroupService userGroupService;

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<?>> getUser(@PathVariable Long id) {
        return SuccessResponse.ok(userService.getUserById(id));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getAllUsers() {
        return SuccessResponse.ok(adminUserService.getAllUsers());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SuccessResponse<?>> deleteUser(@PathVariable Long id) {
        adminUserService.deleteUser(id);
        return SuccessResponse.ok(null);
    }

    @DeleteMapping("/{id}/ubuntu-account")
    public ResponseEntity<SuccessResponse<?>> deleteUbuntuAccount(@PathVariable Long id) {
        adminUserService.deleteUbuntuAccountOfUser(id);
        return SuccessResponse.ok(null);
    }

    /** 활성 상태 변경. {"active": false}면 비활성화(컨테이너·계정 정리), true면 재활성화. */
    @PatchMapping("/{id}")
    public ResponseEntity<SuccessResponse<?>> updateUserActivation(@PathVariable Long id,
                                                                   @RequestBody @Valid UserActivationRequestDTO dto) {
        return SuccessResponse.ok(Boolean.TRUE.equals(dto.active())
                ? adminUserService.reactivateUser(id)
                : adminUserService.deactivateUser(id));
    }

    @GetMapping("/{id}/groups")
    public ResponseEntity<SuccessResponse<?>> getUserGroups(@PathVariable Long id) {
        return SuccessResponse.ok(userGroupService.getGroupsOfUser(id));
    }

    @DeleteMapping("/{id}/groups/{groupId}")
    public ResponseEntity<SuccessResponse<?>> removeUserFromGroup(@PathVariable Long id, @PathVariable Long groupId) {
        userGroupService.removeUserFromGroup(id, groupId);
        return SuccessResponse.ok(null);
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<SuccessResponse<?>> changeUserRole(@PathVariable Long id, @RequestBody @Valid ChangeRoleRequestDTO dto) {
        return SuccessResponse.ok(adminUserService.changeUserRole(id, dto.role()));
    }
}
