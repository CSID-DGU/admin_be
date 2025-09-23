package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.RequestApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SingleChangeRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.SaveRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestCommandService;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestQueryService;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse; // Import SuccessResponse
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

    private final RequestQueryService requestQueryService;
    private final RequestCommandService requestCommandService;

    /**
     * 사용 신청 생성
     */
    @PostMapping
    public ResponseEntity<SuccessResponse<?>> createRequest(@AuthenticationPrincipal(expression = "userId") Long userId,
                                                             @RequestBody @Valid SaveRequestRequestDTO dto
    ) {
        SaveRequestResponseDTO body = requestCommandService.createRequest(userId, dto);
        return SuccessResponse.created(body);
    }

    /**
     * 사용 신청 변경 (단일 변경 요청)
     */
    @PostMapping("/{requestId}/change")
    public ResponseEntity<SuccessResponse<?>> createChangeRequest(@AuthenticationPrincipal(expression = "userId") Long userId,
                                                                   @PathVariable Long requestId,
                                                                   @RequestBody @Valid SingleChangeRequestDTO dto
    ) {
        requestCommandService.createSingleChangeRequest(userId, requestId, dto);
        return SuccessResponse.ok(null);
    }

    /**
     * 나의 사용 신청 조회
     */
    @GetMapping("/my")
    public ResponseEntity<SuccessResponse<?>> getMyRequests(@AuthenticationPrincipal CustomUserDetails user
    ) {
        List<SaveRequestResponseDTO> body = requestQueryService.getRequestsByUserId(user.getUserId());
        return SuccessResponse.ok(body);
    }

    /**
     * 나의 승인 완료된 사용 신청 조회
     */
    @GetMapping("/my/approved")
    public ResponseEntity<SuccessResponse<?>> getMyApprovedRequests(@AuthenticationPrincipal CustomUserDetails user) {
        List<SaveRequestResponseDTO> body = requestQueryService.getApprovedRequestsByUserId(user.getUserId());
        return SuccessResponse.ok(body);
    }

    /**
     * 나의 사용 신청에 대한 모든 ubuntu_username 조회
     */
    @GetMapping("/fulfilled-usernames")
    public ResponseEntity<SuccessResponse<?>> getAllFulfilledUsernames() {
        List<String> usernames = requestQueryService.getAllFulfilledUsernames();
        return SuccessResponse.ok(usernames);
    }
}