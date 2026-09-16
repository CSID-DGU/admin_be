package DGU_AI_LAB.admin_be.global.event;

/**
 * 관리자가 컨테이너 하나를 회수했을 때 발행한다. 우분투 계정과 홈 디렉터리는 남는다.
 *
 * <p>만료 회수({@link RequestExpiredEvent})와 이벤트를 나눈 이유는 통보 문구 때문이다.
 * 만료 안내는 "기간 만료로 인해 삭제되었습니다 / 만료일: ..."이라고 단정하는데, 관리자가 지운 것을
 * 그 문구로 알리면 사용자는 쓰지도 않은 기간이 끝난 줄 안다. 그래서 만료일이 없는 별도 문구를 쓴다.
 */
public record RequestContainerDeletedEvent(
        String userName,
        String userEmail,
        String ubuntuUsername,
        String serverName,
        String podName,
        String portSummary
) {}
