package DGU_AI_LAB.admin_be.domain.requests.controller;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.TestPodCreateRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.service.AdminTestPodService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/test-pods")
@Tag(name = "3. 관리자 - 테스트 Pod")
public class AdminTestPodController {

    private final AdminTestPodService adminTestPodService;

    @Operation(summary = "테스트 Pod 간편 생성",
            description = "노드마다 Pod 하나. 신청·승인 없이 기존 생성 과정을 돌리고 알림은 알림 기록 채널로만 간다. 결과는 컨테이너 목록의 test=true 항목으로 확인한다.")
    @PostMapping
    public ResponseEntity<SuccessResponse<?>> create(@AuthenticationPrincipal(expression = "userId") Long userId,
                                                     @RequestBody @Valid TestPodCreateRequestDTO dto) {
        return SuccessResponse.accepted(adminTestPodService.create(userId, dto));
    }
}
