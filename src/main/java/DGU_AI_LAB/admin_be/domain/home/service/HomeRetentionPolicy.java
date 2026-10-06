package DGU_AI_LAB.admin_be.domain.home.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용이 끝난 계정의 홈을 얼마나 두는가. 사용자에게 알리는 날과 실제로 지우는 날이 다르다 — 알린 날보다 늦게 지워,
 * 알린 날 직전에 다시 신청한 사용자의 승인이 끝날 여유를 둔다.
 */
@Component
public class HomeRetentionPolicy {

    /** 마지막 컨테이너가 끝난 뒤 이만큼 지나면 지운다고 사용자에게 알린다. */
    @Value("${home-retention.announced-days:30}")
    private int announcedDays = 30;

    /** 마지막 컨테이너가 끝난 뒤 실제로 지우기까지의 기간. 실험 스택에서만 짧은 값을 넣는다. */
    @Value("${home-retention.delete-days:40}")
    private int deleteDays = 40;

    public int announcedDays() {
        return announcedDays;
    }

    /** 그날 컨테이너가 끝나면 사용자에게 알릴 삭제일. */
    public LocalDate announcedDeletionDate(LocalDate containerEndDate) {
        return containerEndDate.plusDays(announcedDays);
    }

    /** 마지막 컨테이너가 이 시각보다 먼저 끝난 계정의 홈은 지울 수 있다. */
    public LocalDateTime deletableEndedBefore(LocalDateTime now) {
        return now.minusDays(deleteDays);
    }
}
