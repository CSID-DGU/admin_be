package DGU_AI_LAB.admin_be.domain.messageTemplate.controller;

import DGU_AI_LAB.admin_be.domain.messageTemplate.controller.docs.AdminMessageTemplateApi;
import DGU_AI_LAB.admin_be.domain.messageTemplate.service.MessageTemplateService;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/messages")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMessageTemplateController implements AdminMessageTemplateApi {

    private final MessageTemplateService messageTemplateService;

    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getAll() {
        return SuccessResponse.ok(messageTemplateService.getAll());
    }

    @PatchMapping("/{key}")
    public ResponseEntity<SuccessResponse<?>> update(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {
        // value가 없으면 not-null 제약 위반으로 500이 나가므로 요청 경계에서 400으로 끊는다.
        String value = body == null ? null : body.get("value");
        if (value == null || value.isBlank()) {
            throw new BusinessException("메시지 값(value)은 필수입니다.", ErrorCode.INVALID_INPUT_VALUE);
        }
        messageTemplateService.update(key, value);
        return SuccessResponse.ok(null);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<SuccessResponse<?>> reset(@PathVariable String key) {
        messageTemplateService.reset(key);
        return SuccessResponse.ok(null);
    }
}
