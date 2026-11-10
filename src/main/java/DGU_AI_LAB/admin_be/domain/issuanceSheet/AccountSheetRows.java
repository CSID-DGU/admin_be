package DGU_AI_LAB.admin_be.domain.issuanceSheet;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.domain.warnings.entity.UserSuspension;
import DGU_AI_LAB.admin_be.domain.warnings.entity.UserWarning;
import DGU_AI_LAB.admin_be.domain.warnings.entity.WarningType;
import DGU_AI_LAB.admin_be.domain.warnings.policy.WarningLedger;
import DGU_AI_LAB.admin_be.domain.warnings.policy.WarningPolicy;

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
    /** 사용자 통합 관리 문서의 '경고 카운트' 탭과 같은 머리글(A~M). N~R은 경고 대장과 맞춰 보는 보조 열이다. */
    static final List<String> WARNING_HEADER = List.of(
            "사용자", "처리 일시", "판정", "사유 / 확인 내용", "처리자", "처리 전 경고\n(자동)", "증감\n(자동)",
            "처리 후 경고\n(자동)", "차감 자격\n(자동)", "정지 일수\n(자동)", "정지 시작", "해제 예정\n(자동)",
            "안내 문구\n(자동)", "경고 번호", "사용자 번호", "로그인 아이디", "취소됨", "취소한 경고 번호");
    private static final String WARNING_TITLE = "경고 관리 · Provi 자동 기록";
    private static final String WARNING_NOTE = "Provi가 경고 대장에서 자동으로 기록합니다. 이 탭은 동기화 때마다 "
            + "다시 쓰이므로 직접 수정하지 마세요. N:R은 대장과 대조하기 위한 보조 열입니다.";
    /** 경고 탭의 모양: 1행 제목, 2행 안내, 3행 머리글. 가로 2칸·세로 3행 고정, F~L은 자동 계산 열. */
    static final TabLayout WARNING_LAYOUT = new TabLayout(2, 2,
            List.of(163, 155, 120, 268, 128, 98, 98, 98, 98, 98, 159, 159, 408, 103, 103, 131, 75, 131),
            5, 11, List.of(new TabLayout.Merge(0, 0, 1), new TabLayout.Merge(1, 2, 12)));

    /** 메시지 문구를 채워 주는 창구. {@code MessageUtils::get}이 그대로 맞는다. */
    @FunctionalInterface
    interface Messages {
        String get(String code, Object... args);
    }

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

    /**
     * 경고 대장을 '경고 카운트' 탭 모양의 표로 바꾼다. 앞의 두 행은 제목·안내이고 머리글은 세 번째 행이다.
     *
     * <p>처리 전·후 경고는 그 행이 생긴 시점까지의 대장을 다시 읽어 구한다 — 마지막 행의 처리 후 경고가 사용자 목록의
     * 현재 경고 횟수와 같다. 해제 예정은 계산하지 않고 정지 기록의 실제 종료 시각이다(취소로 일찍 끝났으면 그 시각).
     */
    static List<List<String>> warnings(List<UserWarning> warnings, List<UserSuspension> suspensions, Messages messages) {
        Map<Long, UserSuspension> suspensionByWarning = suspensions.stream()
                .collect(Collectors.toMap(s -> s.getWarning().getWarningId(), s -> s));
        Map<Long, List<UserWarning>> ledgerByUser = warnings.stream()
                .sorted(Comparator.comparing(UserWarning::getWarningId))
                .collect(Collectors.groupingBy(w -> w.getUser().getUserId()));
        Set<Long> canceled = WarningLedger.canceledWarningIds(warnings);
        List<List<String>> rows = new ArrayList<>();
        rows.add(padded(WARNING_TITLE));
        rows.add(padded("안내", "", WARNING_NOTE));
        rows.add(WARNING_HEADER);
        warnings.stream().sorted(Comparator.comparing(UserWarning::getWarningId)).forEach(w -> {
            List<UserWarning> ledger = ledgerByUser.get(w.getUser().getUserId());
            int at = ledger.indexOf(w);
            int before = WarningLedger.replay(ledger.subList(0, at)).count();
            WarningLedger after = WarningLedger.replay(ledger.subList(0, at + 1));
            UserSuspension suspension = suspensionByWarning.get(w.getWarningId());
            // shortcut: 정지 일수를 저장하지 않아 정책으로 다시 구한다. 정책 숫자를 바꾸면 과거 행도 바뀌니, 그때 컬럼으로 저장한다.
            int days = suspension == null ? 0 : WarningPolicy.suspensionDays(after.count());
            rows.add(List.of(
                    text(w.getUser().getName()),
                    time(w.getCreatedAt()),
                    verdict(w.getType()),
                    text(w.getReason()),
                    text(w.getIssuedBy().getName()),
                    String.valueOf(before),
                    signed(after.count() - before),
                    String.valueOf(after.count()),
                    after.deductible() ? "가능" : "불가",
                    days > 0 ? String.valueOf(days) : "",
                    suspension == null ? "" : time(suspension.getStartsAt()),
                    suspension == null ? "" : time(suspension.getEndsAt()),
                    notice(w, after.count(), suspension, days, messages),
                    text(w.getWarningId()),
                    text(w.getUser().getUserId()),
                    text(w.getUser().getUbuntuUsername()),
                    canceled.contains(w.getWarningId()) ? "Y" : "",
                    w.getCanceledWarning() == null ? "" : text(w.getCanceledWarning().getWarningId())));
        });
        return rows;
    }

    /** 사용자에게 실제로 보낸 안내와 같은 문장. 정지 종료는 처음 알린 시각(시작 + 일수)이다. */
    private static String notice(UserWarning w, int countAfter, UserSuspension suspension, int days, Messages messages) {
        String name = w.getUser().getName();
        if (w.getType() != WarningType.GRANT) {
            return messages.get("notification.user.warning.reduced.body", name, w.getReason(), String.valueOf(countAfter));
        }
        if (suspension == null) {
            return messages.get("notification.user.warning.granted.body", name, w.getReason(), String.valueOf(countAfter),
                    String.valueOf(WarningPolicy.SUSPENSION_FROM_COUNT),
                    String.valueOf(WarningPolicy.suspensionDays(WarningPolicy.SUSPENSION_FROM_COUNT)));
        }
        return messages.get("notification.user.warning.suspended.body", name, w.getReason(), String.valueOf(countAfter),
                String.valueOf(days), time(suspension.getStartsAt().plusDays(days)));
    }

    /** 머리글과 같은 칸 수로 채운다 — 탭의 열 수는 첫 행이 정한다. */
    private static List<String> padded(String... cells) {
        List<String> row = new ArrayList<>(List.of(cells));
        while (row.size() < WARNING_HEADER.size()) {
            row.add("");
        }
        return row;
    }

    private static String signed(int delta) {
        return delta > 0 ? "+" + delta : String.valueOf(delta);
    }

    /** 경고 카운트 탭의 판정 값. 부여는 '경고 부과', 절차를 지킨 신고로 줄이는 차감은 '정상 신고'다. */
    private static String verdict(WarningType type) {
        return switch (type) {
            case GRANT -> "경고 부과";
            case DEDUCT -> "정상 신고";
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
