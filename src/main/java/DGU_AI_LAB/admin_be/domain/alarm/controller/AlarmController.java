package DGU_AI_LAB.admin_be.domain.alarm.controller;

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
public class AlarmController {

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
        return ResponseEntity.ok("Slack DM 전송 요청 완료");
    }
}
