package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.controller.docs.AdminUserApi;
import DGU_AI_LAB.admin_be.domain.users.dto.request.ChangeRoleRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.service.AdminUserService;
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

    @DeleteMapping("/ubuntu/{username}")
    public ResponseEntity<SuccessResponse<?>> deleteUbuntuAccount(@PathVariable String username) {
        adminUserService.deleteUbuntuAccount(username);
        return SuccessResponse.ok(null);
    }

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<SuccessResponse<?>> reactivateUser(@PathVariable Long id) {
        return SuccessResponse.ok(adminUserService.reactivateUser(id));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<SuccessResponse<?>> deactivateUser(@PathVariable Long id) {
        return SuccessResponse.ok(adminUserService.deactivateUser(id));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<SuccessResponse<?>> changeUserRole(@PathVariable Long id, @RequestBody @Valid ChangeRoleRequestDTO dto) {
        return SuccessResponse.ok(adminUserService.changeUserRole(id, dto.role()));
    }
}
