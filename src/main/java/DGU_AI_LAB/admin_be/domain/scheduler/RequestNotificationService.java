package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestNotificationService {

    private final RequestRepository requestRepository;
    private final AlarmService alarmService;
    private final MessageUtils messageUtils;

    @Transactional(readOnly = true)
    public void sendPreExpiryNotification(LocalDateTime targetDate, String dayLabel) {
        LocalDateTime start = targetDate.toLocalDate().atStartOfDay();
        LocalDateTime end = targetDate.toLocalDate().atTime(LocalTime.MAX);

        List<Request> requests = requestRepository.findAllByExpiresAtBetweenAndStatus(start, end, Status.FULFILLED);

        for (Request request : requests) {
            try {
                User user = request.getUser();
                String serverName = request.getResourceGroup().getServerName();
                String expireDate = request.getExpiresAt().toLocalDate().toString();
                String subject = messageUtils.get("notification.pre-expiry.subject", dayLabel);
                String message = messageUtils.get("notification.pre-expiry.body",
                        user.getName(), dayLabel, expireDate, serverName, request.getUbuntuUsername());

                alarmService.sendAllAlerts(user.getName(), user.getEmail(), subject, message);

            } catch (Exception e) {
                log.warn("{} 전 알림 실패: {}", dayLabel, e.getMessage());
            }
        }
    }
}
