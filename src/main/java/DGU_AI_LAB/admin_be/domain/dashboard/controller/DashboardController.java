package DGU_AI_LAB.admin_be.domain.dashboard.controller;

import DGU_AI_LAB.admin_be.domain.dashboard.controller.docs.DashBoardApi;
import DGU_AI_LAB.admin_be.domain.dashboard.service.DashboardService;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.UserServerResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.StatusFilter;
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
     * @param principal    현재 로그인한 사용자의 인증 정보 (CustomUserDetails)
     * @param statusFilter 조회할 서버 요청의 상태 필터 (PENDING, FULFILLED, DENIED, DELETED, ALL)
     */
    @GetMapping("/me/servers")
    public ResponseEntity<SuccessResponse<?>> getUserServers(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(name = "status") StatusFilter statusFilter
    ) {
        List<UserServerResponseDTO> userServers = dashboardService.getUserServers(principal.getUserId(), statusFilter);
        return SuccessResponse.ok(userServers);
    }

}