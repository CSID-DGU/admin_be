package DGU_AI_LAB.admin_be.domain.adminActionLog.controller;

import DGU_AI_LAB.admin_be.domain.adminActionLog.controller.docs.AdminActionLogApi;
import DGU_AI_LAB.admin_be.domain.adminActionLog.dto.AdminActionLogSearchRequestDTO;
import DGU_AI_LAB.admin_be.domain.adminActionLog.service.AdminActionLogQueryService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/action-logs")
public class AdminActionLogController implements AdminActionLogApi {

    private final AdminActionLogQueryService queryService;

    @GetMapping
    public ResponseEntity<SuccessResponse<?>> search(@ModelAttribute @Valid AdminActionLogSearchRequestDTO condition) {
        return SuccessResponse.ok(queryService.search(condition));
    }
}
