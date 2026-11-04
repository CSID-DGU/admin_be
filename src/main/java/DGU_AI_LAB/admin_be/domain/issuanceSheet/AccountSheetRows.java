package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.warnings.entity.UserSuspension;
import DGU_AI_LAB.admin_be.domain.warnings.entity.UserWarning;
import DGU_AI_LAB.admin_be.domain.warnings.entity.WarningType;
import DGU_AI_LAB.admin_be.domain.warnings.policy.WarningLedger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 사용자 목록과 경고 내역을 시트에 적을 표로 바꾼다. 비밀번호·해시는 싣지 않는다. 마지막 로그인처럼 로그인마다 바뀌는
 * 값도 싣지 않는다 — 실으면 로그인할 때마다 시트를 다시 써야 한다.
 */
final class AccountSheetRows {

    static final List<String> USER_HEADER = List.of(
            "사용자 번호", "이름", "E-mail", "학번", "학과", "전화번호", "권한", "활성", "로그인 아이디", "UID", "GID",
            "리눅스 계정 상태", "그룹명", "현재 경고 횟수", "가입일");
    static final List<String> WARNING_HEADER = List.of(
            "경고 번호", "사용자 번호", "이름", "로그인 아이디", "종류", "사유", "처리 관리자", "처리 일시",
            "취소됨", "취소한 경고 번호", "정지 시작", "정지 종료");

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private AccountSheetRows() {
    }

    static List<List<String>> users(List<User> users, List<UserWarning> warnings) {
        Map<Long, Integer> countByUser = warnings.stream()
                .sorted(Comparator.comparing(UserWarning::getWarningId))
                .collect(Collectors.groupingBy(w -> w.getUser().getUserId()))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> WarningLedger.replay(e.getValue()).count()));
        List<List<String>> rows = new ArrayList<>();
        rows.add(USER_HEADER);
        users.stream().sorted(Comparator.comparing(User::getUserId)).forEach(user -> rows.add(List.of(
                text(user.getUserId()),
                text(user.getName()),
                text(user.getEmail()),
                text(user.getStudentId()),
                text(user.getDepartment()),
                text(user.getPhone()),
                text(user.getRole()),
                Boolean.TRUE.equals(user.getIsActive()) ? "Y" : "N",
                text(user.getUbuntuUsername()),
                text(user.getUbuntuUid()),
                text(user.getUbuntuGid()),
                text(user.getUbuntuAccountStatus()),
                user.getUserGroups().stream().map(g -> g.getGroup().getGroupName())
                        .sorted().collect(Collectors.joining(", ")),
                String.valueOf(countByUser.getOrDefault(user.getUserId(), 0)),
                time(user.getCreatedAt()))));
        return rows;
    }

    static List<List<String>> warnings(List<UserWarning> warnings, List<UserSuspension> suspensions) {
        Set<Long> canceled = WarningLedger.canceledWarningIds(warnings);
        Map<Long, UserSuspension> suspensionByWarning = suspensions.stream()
                .collect(Collectors.toMap(s -> s.getWarning().getWarningId(), s -> s));
        List<List<String>> rows = new ArrayList<>();
        rows.add(WARNING_HEADER);
        warnings.stream().sorted(Comparator.comparing(UserWarning::getWarningId)).forEach(w -> {
            UserSuspension suspension = suspensionByWarning.get(w.getWarningId());
            rows.add(List.of(
                    text(w.getWarningId()),
                    text(w.getUser().getUserId()),
                    text(w.getUser().getName()),
                    text(w.getUser().getUbuntuUsername()),
                    typeLabel(w.getType()),
                    text(w.getReason()),
                    text(w.getIssuedBy().getName()),
                    time(w.getCreatedAt()),
                    canceled.contains(w.getWarningId()) ? "Y" : "",
                    w.getCanceledWarning() == null ? "" : text(w.getCanceledWarning().getWarningId()),
                    suspension == null ? "" : time(suspension.getStartsAt()),
                    suspension == null ? "" : time(suspension.getEndsAt())));
        });
        return rows;
    }

    private static String typeLabel(WarningType type) {
        return switch (type) {
            case GRANT -> "부여";
            case DEDUCT -> "차감";
            case CANCEL -> "취소";
        };
    }

    private static String time(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
