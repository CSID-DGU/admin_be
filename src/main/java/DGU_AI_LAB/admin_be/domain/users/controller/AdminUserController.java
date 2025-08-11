package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserCreateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.service.UserService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 관리자용 유저 컨트롤러
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<?>> getUser(@PathVariable Long id) {
        return SuccessResponse.ok(userService.getUserById(id));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getAllUsers() {
        return SuccessResponse.ok(userService.getAllUsers());
    }

    @PutMapping("/{id}") // TODO: 관리자용 updateUser와 분리 필요
    public ResponseEntity<SuccessResponse<?>> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequestDTO request) {
        return SuccessResponse.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}") // TODO: soft delete로 수정하기
    public ResponseEntity<SuccessResponse<?>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return SuccessResponse.ok(null);
    }
}
