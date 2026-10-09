package DGU_AI_LAB.admin_be.global.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 단위 테스트는 Flyway를 돌리지 않아, 번호가 겹친 마이그레이션은 배포해서 서버가 뜰 때에야 드러난다
 * ("Found more than one migration with version N"으로 기동 실패). 그 전에 여기서 잡는다.
 */
@DisplayName("DB 마이그레이션 번호")
class MigrationVersionTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Pattern NAME = Pattern.compile("V(\\d+)__.+\\.sql");

    @Test
    @DisplayName("모든 파일이 V<번호>__<설명>.sql 이름이고 번호가 겹치지 않는다")
    void versionsAreUnique() throws IOException {
        List<String> names;
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            names = files.map(file -> file.getFileName().toString()).sorted().toList();
        }
        assertThat(names).isNotEmpty().allMatch(name -> NAME.matcher(name).matches());

        Map<Integer, List<String>> byVersion = names.stream().collect(Collectors.groupingBy(name -> {
            Matcher matcher = NAME.matcher(name);
            matcher.matches();
            return Integer.parseInt(matcher.group(1));
        }));
        assertThat(byVersion.values().stream().filter(sameVersion -> sameVersion.size() > 1).toList())
                .as("번호가 겹치는 마이그레이션").isEmpty();
    }
}
