package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.controller.docs.AdminUserApi;
import DGU_AI_LAB.admin_be.domain.users.service.AdminUserService;
import DGU_AI_LAB.admin_be.domain.users.service.UserService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
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
}
