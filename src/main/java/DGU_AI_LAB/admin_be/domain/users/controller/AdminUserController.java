package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
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
public class AdminUserController {

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

    @PutMapping("/{id}") // TODO: 관리자용 updateUser와 분리 필요
    public ResponseEntity<SuccessResponse<?>> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequestDTO request) {
        return SuccessResponse.ok(adminUserService.updateUser(id, request));
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
}
