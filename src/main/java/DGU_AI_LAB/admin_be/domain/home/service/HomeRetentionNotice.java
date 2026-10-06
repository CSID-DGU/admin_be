package DGU_AI_LAB.admin_be.domain.home.service;

import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.global.util.MessageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 컨테이너 종료를 알리는 메일에 넣는 홈 폴더 안내 문장. 홈은 계정에 딸려 있어, 그 계정의 마지막 컨테이너일 때만
 * 삭제일을 알리고 다른 컨테이너가 더 오래 남으면 유지된다고 알린다. 같은 날 끝나는 컨테이너는 서로 마지막이다.
 */
@Component
@RequiredArgsConstructor
public class HomeRetentionNotice {

    private final RequestRepository requestRepository;
    private final HomeRetentionPolicy retentionPolicy;
    private final MessageUtils messageUtils;

    /** 종료 예정일을 앞둔 컨테이너의 안내. 삭제일은 예정일부터 센다. */
    public String beforeExpiry(Request request) {
        return sentence(request, request.getExpiresAt().toLocalDate(), "notification.home.before-expiry.last");
    }

    /** 방금 끝난(만료·관리자 회수) 컨테이너의 안내. 삭제일은 실제로 끝난 날부터 센다. */
    public String afterEnd(Request request, LocalDate endedOn) {
        return sentence(request, endedOn, "notification.home.after-end.last");
    }

    private String sentence(Request request, LocalDate endDate, String lastContainerKey) {
        String username = request.getUbuntuUsername();
        boolean otherContainerOutlives = requestRepository
                .existsByUser_UserIdAndRequestIdNotAndStatusInAndExpiresAtGreaterThanEqual(
                        request.getUser().getUserId(), request.getRequestId(), Status.activeStatuses(),
                        endDate.plusDays(1).atStartOfDay());
        if (otherContainerOutlives) {
            return messageUtils.get("notification.home.kept", username);
        }
        return messageUtils.get(lastContainerKey, username,
                retentionPolicy.announcedDeletionDate(endDate).toString(),
                String.valueOf(retentionPolicy.announcedDays()));
    }
}
