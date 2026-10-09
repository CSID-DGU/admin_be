package DGU_AI_LAB.admin_be.domain.alarm.dto;

import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeType;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.entity.Status;
import DGU_AI_LAB.admin_be.domain.users.entity.User;

/**
 * 변경 요청의 결과(승인되어 반영 끝남·거절됨) 알림에 적을 값. 트랜잭션 안에서 엔티티를 읽어 만들고, 전송은 커밋 뒤에
 * 이 값만으로 한다.
 *
 * @param serverName     대상 신청의 서버. 계정 단위 변경(PASSWORD)이면 null
 * @param approved       승인되어 반영까지 끝났으면 true, 거절됐으면 false
 * @param adminComment   관리자가 남긴 메모. 없으면 null(관리자가 직접 정한 비밀번호)
 * @param slackMessageTs 접수 알림 메시지의 식별자. 모르면 null — 결과는 일반 메시지로 간다
 */
public record ChangeRequestDecision(
        Long changeRequestId,
        ChangeType changeType,
        String name,
        String email,
        String serverName,
        boolean approved,
        String adminComment,
        String slackMessageTs
) {
    /** 끝난(FULFILLED·DENIED) 변경 요청으로 만든다. */
    public static ChangeRequestDecision of(ChangeRequest changeRequest) {
        User user = changeRequest.getRequestedBy();
        Request request = changeRequest.getRequest();
        return new ChangeRequestDecision(
                changeRequest.getChangeRequestId(),
                changeRequest.getChangeType(),
                user.getName(),
                user.getEmail(),
                request == null ? null : request.getResourceGroup().getServerName(),
                changeRequest.getStatus() == Status.FULFILLED,
                changeRequest.getAdminComment(),
                changeRequest.getSlackMessageTs());
    }
}
