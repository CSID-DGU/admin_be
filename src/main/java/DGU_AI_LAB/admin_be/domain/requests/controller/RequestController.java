package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.ModifyRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestService;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService requestService;

    @PostMapping
    public ResponseEntity<SaveRequestResponseDTO> createRequest(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @RequestBody @Valid SaveRequestRequestDTO dto
    ) {
        SaveRequestResponseDTO body = requestService.createRequest(userId, dto);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/my")
    public ResponseEntity<List<SaveRequestResponseDTO>> getMyRequests(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(requestService.getRequestsByUserId(user.getUserId()));
    }

    /*@PostMapping("/modify")
    public ResponseEntity<Void> requestModification(@RequestBody ModifyRequestDTO dto) {
        requestService.requestModification(dto);
        return ResponseEntity.ok().build();
    }*/
}
