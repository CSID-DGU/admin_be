package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserCreateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserUpdateRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.users.service.UserService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<?>> getUser(@PathVariable Long id) {
        return SuccessResponse.ok(userService.getUserById(id));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getAllUsers() {
        return SuccessResponse.ok(userService.getAllUsers());
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<?>> createUser(@Valid @RequestBody UserCreateRequestDTO request) {
        return SuccessResponse.ok(userService.createUser(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuccessResponse<?>> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequestDTO request) {
        return SuccessResponse.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SuccessResponse<?>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return SuccessResponse.ok(null);
    }
}

