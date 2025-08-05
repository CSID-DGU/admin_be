package DGU_AI_LAB.admin_be.domain.alarm.controller;

import DGU_AI_LAB.admin_be.domain.alarm.controller.docs.AlarmApi;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/alert")
public class AlarmController implements AlarmApi {

    private final AlarmService alarmService;

    @GetMapping("/send")
    public ResponseEntity<String> sendAlert(@RequestParam("message") String message) {
        alarmService.sendSlackAlert(message);
        return ResponseEntity.ok("Alert sent to Slack");
    }

    @PostMapping("/dm")
    public ResponseEntity<String> sendSlackDMAlert(
            @RequestParam("username") String username,
            @RequestParam("email") String email,
            @RequestParam("message") String message
    ) {
        alarmService.sendDMAlert(username, email, message);
        return ResponseEntity.ok("Alert sent to Slack DM");
    }

    @PostMapping("/email")
    public ResponseEntity<?> sendEmailAlert(
            @RequestParam String to,
            @RequestParam String subject,
            @RequestParam String body
    ) {
        alarmService.sendMailAlert(to, subject, body);
        return ResponseEntity.ok("Alert sent to Email");
    }

    @PostMapping
    public ResponseEntity<String> sendAllAlerts(
            @RequestParam("username") String username,
            @RequestParam("email") String email,
            @RequestParam("subject") String subject,
            @RequestParam("message") String message
    ) {
        alarmService.sendAllAlerts(username, email, subject, message);
        return ResponseEntity.ok("Slack DM 및 이메일 전송 완료");
    }
}