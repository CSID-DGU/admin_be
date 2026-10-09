package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.controller.docs.MyChangeRequestApi;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.SingleChangeRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestCommandService;
import DGU_AI_LAB.admin_be.domain.requests.service.RequestQueryService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** 로그인한 사용자의 변경 요청. 비밀번호 변경은 메일 인증이 필요해 /api/auth/password-resets 로 낸다. */
@RestController
@RequiredArgsConstructor
@RequestMapping(MyChangeRequestController.BASE_PATH)
public class MyChangeRequestController implements MyChangeRequestApi {

    static final String BASE_PATH = "/api/users/me/change-requests";

    private final RequestCommandService requestCommandService;
    private final RequestQueryService requestQueryService;

    @PostMapping
    public ResponseEntity<SuccessResponse<?>> createChangeRequest(@AuthenticationPrincipal(expression = "userId") Long userId,
                                                                   @RequestBody @Valid SingleChangeRequestDTO dto) {
        ChangeRequestResponseDTO created = requestCommandService.createSingleChangeRequest(userId, dto);
        return SuccessResponse.created(URI.create(BASE_PATH + "/" + created.changeRequestId()), created);
    }

    @GetMapping("/{changeRequestId}")
    public ResponseEntity<SuccessResponse<?>> getMyChangeRequest(@AuthenticationPrincipal(expression = "userId") Long userId,
                                                                  @PathVariable Long changeRequestId) {
        return SuccessResponse.ok(requestQueryService.getMyChangeRequest(userId, changeRequestId));
    }

    @DeleteMapping("/{changeRequestId}")
    public ResponseEntity<Void> cancelChangeRequest(@AuthenticationPrincipal(expression = "userId") Long userId,
                                                    @PathVariable Long changeRequestId) {
        requestCommandService.cancelChangeRequest(userId, changeRequestId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getMyChangeRequests(@AuthenticationPrincipal(expression = "userId") Long userId) {
        return SuccessResponse.ok(requestQueryService.getMyChangeRequests(userId));
    }
}
