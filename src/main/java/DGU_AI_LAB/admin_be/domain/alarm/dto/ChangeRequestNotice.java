package DGU_AI_LAB.admin_be.domain.alarm.dto;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.users.entity.User;

import java.time.LocalDateTime;

/**
 * 변경 요청 접수 알림에 적을 값. 트랜잭션 안에서 엔티티를 읽어 만들고, 전송은 커밋 뒤에 이 값만으로 한다.
 *
 * @param serverName 대상 신청의 서버. 계정 단위 변경(PASSWORD)이면 null
 * @param requestId  대상 신청 번호. 계정 단위 변경이면 null
 * @param change     무엇이 어떻게 바뀌는지 한 줄(이미 Slack 글로 다듬은 값)
 * @param reason     신청자가 적은 사유. 받지 않는 종류면 null
 */
public record ChangeRequestNotice(
        Long changeRequestId,
        ChangeType changeType,
        LocalDateTime receivedAt,
        String name,
        String studentId,
        String department,
        String email,
        String ubuntuUsername,
        String serverName,
        Long requestId,
        String change,
        String reason
) {
    public static ChangeRequestNotice of(ChangeRequest changeRequest, String change) {
        User user = changeRequest.getRequestedBy();
        Request request = changeRequest.getRequest();
        return new ChangeRequestNotice(
                changeRequest.getChangeRequestId(),
                changeRequest.getChangeType(),
                changeRequest.getCreatedAt(),
                user.getName(),
                user.getStudentId(),
                user.getDepartment(),
                user.getEmail(),
                request == null ? user.getUbuntuUsername() : request.getUbuntuUsername(),
                request == null ? null : request.getResourceGroup().getServerName(),
                request == null ? null : request.getRequestId(),
                change,
                changeRequest.getReason());
    }
}
