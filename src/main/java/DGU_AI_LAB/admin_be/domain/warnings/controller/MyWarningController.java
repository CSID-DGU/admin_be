package DGU_AI_LAB.admin_be.domain.warnings.controller;

import DGU_AI_LAB.admin_be.domain.warnings.service.WarningService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "경고", description = "본인의 경고·이용 정지 현황")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/warnings")
public class MyWarningController {

    private final WarningService warningService;

    @Operation(summary = "내 경고 현황 조회", description = "현재 횟수, 이용 정지 종료 시각, 내역을 돌려줍니다. 정지 중에도 조회할 수 있습니다.")
    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getMyWarnings(@AuthenticationPrincipal(expression = "userId") Long userId) {
        return SuccessResponse.ok(warningService.getOwnStatus(userId));
    }
}
