package DGU_AI_LAB.admin_be.domain.requests.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Baseline 이 "완료"라고 선언하는 자리를 전부 세어서 고정하는 계약 테스트.
 *
 * <p>실험 하네스는 Baseline 의 완료 선언을 system_declaration 칸에 모은다. 선언을 만드는 자리가
 * 코드 안에 몇 군데인지 모르면 하네스가 그중 일부만 수집해 놓고 전부 수집했다고 믿게 되고,
 * 그 순간 잘못된 완료 선언률의 분모가 조용히 작아진다.
 *
 * <p>왜 Request.java 소스 파일을 읽는가: 운영 코드에 계측용 애너테이션이나 표시를 심지 않고도
 * 진입점 목록을 셀 수 있고, 시험이 실패했을 때 사람이 무엇을 결정해야 하는지가 실패 메시지에
 * 그대로 드러나기 때문이다. 리플렉션으로는 "어느 메서드가 status 를 FULFILLED 로 바꾸는가"를
 * 알 수 없으므로 소스를 읽는다. 자바 파서를 새로 만들지 않고, 줄 단위로 훑으면서 가장 최근에
 * 지나온 메서드 선언 이름을 기억하는 방식으로 충분하다.
 *
 * <p>이 시험이 실패하면 상태 전이 메서드를 고치지 말고, 새 진입점의 뜻을 아래 표의 어느 칸에
 * 넣을지부터 사람이 결정한다.
 *
 * <pre>
 * completeApproval    생성 작업이 성공해서 승인을 확정한다   → 생성의 시스템 선언으로 센다
 * deleteAfterCleanup  회수 정리가 끝나 삭제를 확정한다       → 회수의 시스템 선언으로 센다
 * approve             동기 경로의 즉시 승인이다              → 비동기 경로를 쓰는 실험 대상이 아니다
 * endMigration        마이그레이션이 끝나 원래대로 돌아간다  → 마이그레이션은 실험 범위 밖이다
 * endExpiry           회수에 실패해서 되돌린다               → 선언이 아니라 되돌리기다
 * delete              사용자나 관리자가 신청을 취소한다      → 회수 절차를 거치지 않았으므로 선언이 아니다
 * </pre>
 */
class StatusDeclarationContractTest {

    private static final Path REQUEST_SOURCE =
            Path.of("src/main/java/DGU_AI_LAB/admin_be/domain/requests/entity/Request.java");

    /** 들여쓰기된 메서드/생성자 선언 한 줄에서 이름만 뽑는다. 제어문(if·for 등)은 이름 검사로 걸러낸다. */
    private static final Pattern METHOD_DECLARATION =
            Pattern.compile("^\\s+(?:public|protected|private)\\s+[\\w<>\\[\\],.\\s]*?(\\w+)\\s*\\(");

    private static final String DECIDE =
            "새 진입점을 발견하면 실험의 시스템 선언 집계에 넣을지 사람이 결정해야 한다. "
                    + "시험을 통과시키려고 기대 집합을 실제 집합에 맞춰 넓히지 않는다.";

    @Test
    @DisplayName("FULFILLED 로 바꾸는 진입점은 approve·completeApproval·endMigration·endExpiry 네 개뿐이다")
    void fulfilledDeclarationSitesAreFixed() throws IOException {
        Set<String> actual = methodsAssigning("FULFILLED");

        assertThat(actual)
                .withFailMessage(
                        "status 를 FULFILLED 로 바꾸는 메서드 집합이 달라졌다.%n"
                                + "기대한 집합: %s%n실제 집합: %s%n%s",
                        Set.of("approve", "completeApproval", "endExpiry", "endMigration"), actual, DECIDE)
                .containsExactlyInAnyOrder("approve", "completeApproval", "endMigration", "endExpiry");
    }

    @Test
    @DisplayName("DELETED 로 바꾸는 진입점은 delete·deleteAfterCleanup 두 개뿐이다")
    void deletedDeclarationSitesAreFixed() throws IOException {
        Set<String> actual = methodsAssigning("DELETED");

        assertThat(actual)
                .withFailMessage(
                        "status 를 DELETED 로 바꾸는 메서드 집합이 달라졌다.%n"
                                + "기대한 집합: %s%n실제 집합: %s%n%s",
                        Set.of("delete", "deleteAfterCleanup"), actual, DECIDE)
                .containsExactlyInAnyOrder("delete", "deleteAfterCleanup");
    }

    @Test
    @DisplayName("여섯 진입점 중 실험이 시스템 선언으로 세는 것은 completeApproval 과 deleteAfterCleanup 두 개다")
    void onlyTwoSitesCountAsSystemDeclaration() throws IOException {
        Set<String> all = new TreeSet<>(methodsAssigning("FULFILLED"));
        all.addAll(methodsAssigning("DELETED"));

        assertThat(all)
                .withFailMessage(
                        "완료 선언 지점의 총수가 여섯 개가 아니다.%n기대한 집합: %s%n실제 집합: %s%n%s",
                        Set.of("approve", "completeApproval", "delete", "deleteAfterCleanup", "endExpiry", "endMigration"),
                        all, DECIDE)
                .hasSize(6);

        // 실험이 집계하는 두 자리다. 나머지 네 자리는 뜻이 달라서 세지 않는다.
        assertThat(all)
                .withFailMessage("시스템 선언으로 세는 두 메서드가 Request.java 에서 사라졌다.%n실제 집합: %s%n%s", all, DECIDE)
                .contains("completeApproval", "deleteAfterCleanup");
    }

    /** {@code this.status = Status.<value>;} 를 담고 있는 메서드 이름을 모은다. */
    private Set<String> methodsAssigning(String statusValue) throws IOException {
        assertThat(REQUEST_SOURCE).exists();
        List<String> lines = Files.readAllLines(REQUEST_SOURCE, StandardCharsets.UTF_8);
        String assignment = "this.status = Status." + statusValue + ";";

        Map<String, String> found = new LinkedHashMap<>();
        String currentMethod = null;
        for (String line : lines) {
            Matcher m = METHOD_DECLARATION.matcher(line);
            if (m.find()) {
                currentMethod = m.group(1);
            }
            if (line.contains(assignment) && currentMethod != null) {
                found.put(currentMethod, currentMethod);
            }
        }
        return new TreeSet<>(found.keySet());
    }
}
