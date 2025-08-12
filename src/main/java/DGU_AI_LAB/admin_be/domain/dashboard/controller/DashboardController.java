package DGU_AI_LAB.admin_be.domain.dashboard.controller;

import DGU_AI_LAB.admin_be.domain.dashboard.service.DashboardService;
import DGU_AI_LAB.admin_be.domain.gpus.dto.response.GpuTypeResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.UserServerResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;


    /**
     * 사용자 신청 현황 서버 목록 조회 API -> 다른 곳으로 옮겨야 함 (Request)
     * GET /api/dashboard/me/servers
     *
     * @param principal 현재 로그인한 사용자의 인증 정보 (CustomUserDetails)
     * @param status    조회할 서버 요청의 상태 (필수 값: PENDING, FULFILLED, DENIED 등)
     * 사용자의 승인받은 서버 목록 또는 승인 대기중인 신청 목록을 필터링하여 반환합니다.
     */
    @GetMapping("/me/servers")
    public ResponseEntity<SuccessResponse<?>> getUserServers(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(name = "status") Status status
    ) {
        // 현재 로그인한 사용자의 ID와 요청 상태를 기반으로 서버 목록을 조회합니다.
        log.info("[getUserServers] 사용자 서버 목록 조회 API 호출 - userId: {}, status: {}", principal.getUserId(), status);
        List<UserServerResponseDTO> userServers = dashboardService.getUserServers(principal.getUserId(), status);
        return SuccessResponse.ok(userServers);
    }
}