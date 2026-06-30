package DGU_AI_LAB.admin_be.domain.messageTemplate.controller;

import DGU_AI_LAB.admin_be.domain.messageTemplate.service.MessageTemplateService;
import DGU_AI_LAB.admin_be.global.common.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 관리자용 알림 메시지 템플릿 CRUD API.
 *
 * 엔드포인트 설계:
 *   GET    /api/admin/messages       전체 목록 (기본값 + 오버라이드 상태)
 *   PATCH  /api/admin/messages/{key} 특정 키 수정 (DB에 오버라이드 저장)
 *   DELETE /api/admin/messages/{key} 오버라이드 삭제 (기본값으로 복원)
 *
 * PUT이 아니라 PATCH를 쓰는 이유:
 *   PUT은 "리소스 전체를 교체"하는 의미, PATCH는 "일부만 수정"하는 의미
 *   여기선 value 하나만 바꾸니까 PATCH가 의미적으로 맞음
 *
 * SecurityConfig에서 /api/admin/** 경로에 ADMIN 역할 체크가 주석 처리되어 있어서
 * 현재는 JWT 인증만 통과하면 접근 가능. ADMIN 전용으로 막으려면 SecurityConfig 주석 해제
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/messages")
public class AdminMessageTemplateController {

    private final MessageTemplateService messageTemplateService;

    @GetMapping
    public ResponseEntity<SuccessResponse<?>> getAll() {
        return SuccessResponse.ok(messageTemplateService.getAll());
    }

    /**
     * @param key  메시지 키 — URL에 dot 포함됨 (ex: notification.expired.dm)
     *             Spring Boot 3.x는 suffix pattern matching이 기본 OFF라 dot이 잘리지 않음.
     * @param body {"value": "수정할 메시지 내용"} — 별도 DTO 대신 Map으로 받음.
     *             필드가 value 하나뿐이라 DTO 클래스를 따로 만들면 오버엔지니어링.
     */
    @PatchMapping("/{key}")
    public ResponseEntity<SuccessResponse<?>> update(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {
        messageTemplateService.update(key, body.get("value"));
        return SuccessResponse.ok(null);
    }

    /**
     * DB 오버라이드를 삭제하면 해당 키는 messages.properties 기본값으로 자동 복원됨.
     * "초기화" 버튼에 연결하면 됨.
     */
    @DeleteMapping("/{key}")
    public ResponseEntity<SuccessResponse<?>> reset(@PathVariable String key) {
        messageTemplateService.reset(key);
        return SuccessResponse.ok(null);
    }
}
