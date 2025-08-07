package DGU_AI_LAB.admin_be.domain.alarm.controller;

import DGU_AI_LAB.admin_be.domain.alarm.controller.docs.AlarmApi;
import DGU_AI_LAB.admin_be.domain.alarm.dto.request.CombinedAlertRequestDTO;
import DGU_AI_LAB.admin_be.domain.alarm.dto.request.EmailRequestDTO;
import DGU_AI_LAB.admin_be.domain.alarm.dto.request.SlackDMRequestDTO;
import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/alert")
public class AlarmController implements AlarmApi {

    private final AlarmService alarmService;

    @PostMapping("/dm")
    public ResponseEntity<String> sendSlackDMAlert(@RequestBody @Valid SlackDMRequestDTO request) {
        alarmService.sendDMAlert(request.username(), request.email(), request.message());
        return ResponseEntity.ok("Alert sent to Slack DM");
    }

    @PostMapping("/email")
    public ResponseEntity<?> sendEmailAlert(@RequestBody @Valid EmailRequestDTO request) {
        alarmService.sendMailAlert(request.to(), request.subject(), request.body());
        return ResponseEntity.ok("Alert sent to Email");
    }

    @PostMapping
    public ResponseEntity<String> sendAllAlerts(@RequestBody @Valid CombinedAlertRequestDTO request) {
        alarmService.sendAllAlerts(
                request.username(),
                request.email(),
                request.subject(),
                request.message()
        );
        return ResponseEntity.ok("Alert sent to Slack DM and Email");
    }
}