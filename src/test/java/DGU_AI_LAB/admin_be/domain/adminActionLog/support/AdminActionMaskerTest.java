package DGU_AI_LAB.admin_be.domain.adminActionLog.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminActionMaskerTest {

    private final AdminActionMasker masker = new AdminActionMasker(new ObjectMapper());

    record Reset(String newPassword) {
    }

    @Test
    @DisplayName("칸 이름에 password·token·secret 이 들어가면 대소문자와 깊이에 상관없이 가린다")
    void masksSecretFieldsByName() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("newPassword", "abcd1234!");
        body.put("adminComment", "확인함");
        body.put("nested", Map.of("API_TOKEN", "t-1", "clientSecret", Map.of("v", "s")));
        body.put("list", List.of(Map.of("password", "p")));

        String masked = masker.maskBody(body);

        assertThat(masked).doesNotContain("abcd1234!", "t-1", "\"s\"", "\"p\"");
        assertThat(masked).contains("\"adminComment\":\"확인함\"");
        assertThat(masked).contains("\"newPassword\":\"***\"", "\"API_TOKEN\":\"***\"",
                "\"clientSecret\":\"***\"", "\"password\":\"***\"");
    }

    @Test
    @DisplayName("레코드 DTO 도 같은 규칙으로 가린다")
    void masksRecordDto() {
        assertThat(masker.maskBody(new Reset("abcd1234!"))).isEqualTo("{\"newPassword\":\"***\"}");
    }

    @Test
    @DisplayName("쿼리 파라미터도 이름으로 가리고, 본문·파라미터가 없으면 null 이다")
    void masksQueryParams() {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("reason", new String[]{"오탐"});
        params.put("token", new String[]{"x"});

        assertThat(masker.maskParams(params)).isEqualTo("{\"reason\":\"오탐\",\"token\":\"***\"}");
        assertThat(masker.maskParams(Map.of())).isNull();
        assertThat(masker.maskBody(null)).isNull();
    }
}
