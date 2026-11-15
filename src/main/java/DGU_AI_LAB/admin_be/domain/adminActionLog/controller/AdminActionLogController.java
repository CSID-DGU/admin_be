package DGU_AI_LAB.admin_be.domain.adminActionLog.controller;

import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.request.AdminActionLogRequestDTO;
import DGU_AI_LAB.admin_be.domain.adminActionLog.service.AdminActionLogService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/action-logs")
public class AdminActionLogController {

    private final AdminActionLogService adminActionLogService;

    @GetMapping
    public ResponseEntity<SuccessResponse<?>> search(@ModelAttribute AdminActionLogRequestDTO request) {
        return SuccessResponse.ok(adminActionLogService.search(request));
    }
}