package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.RequestApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.ModifyRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestCommandService;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestQueryService;
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
public class RequestController implements RequestApi {

    private final RequestQueryService requestService;
    private final RequestCommandService requestCommandService;

    /**
     * 사용 신청 생성
     */
    @PostMapping
    public ResponseEntity<SaveRequestResponseDTO> createRequest(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @RequestBody @Valid SaveRequestRequestDTO dto
    ) {
        SaveRequestResponseDTO body = requestCommandService.createRequest(userId, dto);
        return ResponseEntity.ok(body);
    }

    /**
     * 사용 신청 변경 (저장공간 크기, 만료기한, 사용자가 속한 그룹, 리소스 그룹, 도커 이미지)
     */
    @PostMapping("/{requestId}/change")
    public ResponseEntity<Void> createChangeRequest(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @PathVariable Long requestId,
            @RequestBody @Valid ModifyRequestDTO dto
    ) {
        requestCommandService.createModificationRequest(userId, requestId, dto);
        return ResponseEntity.ok().build();
    }

    /**
     * 나의 사용 신청 조회
     */
    @GetMapping("/my")
    public ResponseEntity<List<SaveRequestResponseDTO>> getMyRequests(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(requestService.getRequestsByUserId(user.getUserId()));
    }

}
