package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import DGU_AI_LAB.admin_be.domain.containerImage.entity.ContainerImage;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.users.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 컨테이너가 살아 있는 신청들을 시트에 적을 표(머리글 + 행)로 바꾼다. 열 순서는 기존 수동 발급 시트를 따른다. */
final class IssuanceSheetRows {

    static final List<String> HEADER = List.of(
            "신청 번호", "이름", "로그인 아이디", "그룹명", "배정 서버", "UID", "GID", "포트 번호",
            "서버 사용 완료 예정일", "스토리지 삭제 예정일", "컨테이너 생성일자", "docker image version", "cuda version",
            "컨테이너 명", "E-mail", "전화번호", "비고", "리소스 그룹");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Comparator<Request> ORDER = Comparator.comparing(Request::getRequestId);

    private IssuanceSheetRows() {
    }

    /**
     * @param requests              한 서버에서 컨테이너가 살아 있는 신청들
     * @param portsByRequest        신청 번호별 접속 포트 표기(적을 순서대로)
     * @param homeDeletionDateByUser 사용자 번호별 홈 삭제 예정일
     */
    static List<List<String>> of(List<Request> requests, Map<Long, List<String>> portsByRequest,
                                 Map<Long, LocalDate> homeDeletionDateByUser) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(HEADER);
        requests.stream().sorted(ORDER)
                .map(request -> row(request,
                        portsByRequest.getOrDefault(request.getRequestId(), List.of()),
                        homeDeletionDateByUser.get(request.getUser().getUserId())))
                .forEach(rows::add);
        return rows;
    }

    private static List<String> row(Request request, List<String> ports, LocalDate homeDeletionDate) {
        User user = request.getUser();
        ContainerImage image = request.getContainerImage();
        return List.of(
                text(request.getRequestId()),
                text(user.getName()),
                text(user.getUbuntuUsername()),
                request.getRequestGroups().stream()
                        .map(requestGroup -> requestGroup.getGroup().getGroupName())
                        .sorted().collect(Collectors.joining(", ")),
                text(request.getNodeName()),
                text(user.getUbuntuUid()),
                text(user.getUbuntuGid()),
                String.join(", ", ports),
                date(request.getExpiresAt()),
                text(homeDeletionDate),
                date(request.getApprovedAt()),
                image.getImageName() + ":" + image.getImageVersion(),
                text(image.getCudaVersion()),
                text(request.getPodName()),
                text(user.getEmail()),
                text(user.getPhone()),
                text(request.getAdminComment()),
                text(request.getResourceGroup().getResourceGroupName()));
    }

    private static String date(LocalDateTime value) {
        return value == null ? "" : value.format(DATE);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
