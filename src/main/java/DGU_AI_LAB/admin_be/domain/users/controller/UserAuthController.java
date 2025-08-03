package DGU_AI_LAB.admin_be.domain.users.controller;

import DGU_AI_LAB.admin_be.domain.approval.dto.response.ApprovalResponseDTO;
import DGU_AI_LAB.admin_be.domain.approval.service.ApprovalService;
import DGU_AI_LAB.admin_be.domain.users.dto.request.UserAuthRequestDTO;
import DGU_AI_LAB.admin_be.domain.users.dto.response.UserAuthResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/users")
@RequiredArgsConstructor
public class UserAuthController {

    private final ApprovalService approvalService;

    @PostMapping
    public UserAuthResponseDTO userAuth(@RequestBody UserAuthRequestDTO request) {
        ApprovalResponseDTO approvalResponse = approvalService.userAuth(request);
        return new UserAuthResponseDTO(true, approvalResponse.username());
    }
}
