package DGU_AI_LAB.admin_be.domain.scheduler;

import DGU_AI_LAB.admin_be.domain.alarm.service.AlarmService;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional(readOnly = true)
    public void sendPreExpiryNotification(LocalDateTime targetDate, String dayLabel) {
        LocalDateTime start = targetDate.toLocalDate().atStartOfDay();
        LocalDateTime end = targetDate.toLocalDate().atTime(LocalTime.MAX);

        List<Request> requests = requestRepository.findAllByExpiresAtBetweenAndStatus(start, end, Status.FULFILLED);
        String today = targetDate.toLocalDate().toString();

        for (Request request : requests) {
            try {
                if (isDuplicate(request.getRequestId(), dayLabel, today)) {
                    log.debug("만료 예고 중복 발송 방지: requestId={}, dayLabel={}, date={}", request.getRequestId(), dayLabel, today);
                    continue;
                }

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

    /**
     * Redis SETNX로 당일 중복 발송을 방지한다.
     * Redis 장애 시 false를 반환해 이메일 발송을 허용한다(fail-open).
     */
    private boolean isDuplicate(Long requestId, String dayLabel, String date) {
        try {
            String key = "email:preexpiry:" + requestId + ":" + dayLabel + ":" + date;
            Boolean set = redisTemplate.opsForValue().setIfAbsent(key, "sent", Duration.ofHours(25));
            return Boolean.FALSE.equals(set);
        } catch (Exception e) {
            log.warn("Redis 중복 체크 실패, 발송 진행: {}", e.getMessage());
            return false;
        }
    }
}
