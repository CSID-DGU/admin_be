package DGU_AI_LAB.admin_be.domain.messageTemplate.controller;

import DGU_AI_LAB.admin_be.domain.messageTemplate.controller.docs.AdminMessageTemplateApi;
import DGU_AI_LAB.admin_be.domain.messageTemplate.service.MessageTemplateService;
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
        messageTemplateService.update(key, body.get("value"));
        return SuccessResponse.ok(null);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<SuccessResponse<?>> reset(@PathVariable String key) {
        messageTemplateService.reset(key);
        return SuccessResponse.ok(null);
    }
}
