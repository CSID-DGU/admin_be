package DGU_AI_LAB.admin_be.domain.adminActionLog.controller;

import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminActionType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminTargetType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.service.AdminActionLogQueryService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/** 기존 /api/admin/** 권한 규칙을 사용하며 수정·삭제 API는 제공하지 않는다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/action-logs")
public class AdminActionLogController {
    private final AdminActionLogQueryService queryService;

    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getLogs(
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) AdminTargetType targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) AdminActionType actionType,
            @RequestParam(required = false) String requestedRole,
            @RequestParam(required = false) Integer httpStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime to,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int size) {
        return SuccessResponse.ok(
                queryService.getLogs(
                        adminId,
                        targetType,
                        targetId,
                        actionType,
                        requestedRole,
                        httpStatus,
                        from,
                        to,
                        cursor,
                        size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<?>> getLog(@PathVariable Long id) {
        return SuccessResponse.ok(queryService.getLog(id));
    }
}
