package DGU_AI_LAB.admin_be.global.cluster;

import java.util.Locale;
import java.util.Set;

/** 클러스터 식별자를 설정 키와 비교할 수 있는 한 가지 표기로 바꾼다. */
public final class ClusterId {

    private static final Set<String> SUPPORTED = Set.of("FARM", "LAB");

    private ClusterId() {
    }

    /**
     * {@code ResourceGroup.serverName}과 설정 키는 대소문자를 구분하지 않는다.
     * 값이 없을 때 기본 클러스터를 추측하지 않고 즉시 실패시킨다.
     */
    public static String canonical(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("clusterId는 비어 있을 수 없습니다.");
        }
        String canonical = value.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED.contains(canonical)) {
            throw new IllegalArgumentException("지원하지 않는 clusterId입니다: " + canonical);
        }
        return canonical;
    }
}
