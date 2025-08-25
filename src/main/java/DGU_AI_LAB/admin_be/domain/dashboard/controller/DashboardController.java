package DGU_AI_LAB.admin_be.domain.dashboard.controller;

import DGU_AI_LAB.admin_be.domain.dashboard.controller.docs.DashBoardApi;
import DGU_AI_LAB.admin_be.domain.dashboard.service.DashboardService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.ChangeRequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.UserServerResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.global.auth.CustomUserDetails;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardController implements DashBoardApi {

    private final DashboardService dashboardService;

    /**
     * 사용자 신청 현황 서버 목록 조회 API
     *
     * @param principal 현재 로그인한 사용자의 인증 정보 (CustomUserDetails)
     * @param status    조회할 서버 요청의 상태 (필수 값: PENDING, FULFILLED, DENIED, ALL)
     */
    @GetMapping("/me/servers")
    public ResponseEntity<SuccessResponse<?>> getUserServers(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(name = "status") Status status
    ) {
        List<UserServerResponseDTO> userServers = dashboardService.getUserServers(principal.getUserId(), status);
        return SuccessResponse.ok(userServers);
    }

    /**
     * 사용자의 변경 요청 목록 조회 API
     *
     * @param principal 현재 로그인한 사용자의 인증 정보 (CustomUserDetails)
     * @param status    조회할 변경 요청의 상태 (PENDING, APPROVED, DENIED)
     */
    @GetMapping("/me/change-requests")
    public ResponseEntity<SuccessResponse<?>> getMyChangeRequests(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(name = "status") Status status
    ) {
        List<ChangeRequestResponseDTO> changeRequests = dashboardService.getMyChangeRequests(principal.getUserId(), status);
        return SuccessResponse.ok(changeRequests);
    }
}