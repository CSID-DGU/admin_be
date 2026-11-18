package DGU_AI_LAB.admin_be.domain.adminActionLog.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 기록에 남기기 전에 비밀 값을 가린다. 칸 이름에 password·token·secret 이 들어가면(대소문자 무시) 값을
 * {@value #MASK}로 바꾼다. 중첩 객체와 배열 안까지, 쿼리 파라미터에도 같은 규칙을 쓴다.
 * 칸 이름으로 고르므로 새 DTO 에 newPassword 같은 칸이 생겨도 따로 등록할 필요가 없다.
 */
@Component
@RequiredArgsConstructor
public class AdminActionMasker {

    public static final String MASK = "***";
    private static final Pattern SECRET_KEY = Pattern.compile("(?i).*(password|token|secret).*");

    private final ObjectMapper objectMapper;

    /** 본문 객체를 가린 JSON 문자열로. 없으면 null. */
    public String maskBody(Object body) {
        if (body == null) {
            return null;
        }
        return write(mask(objectMapper.valueToTree(body)));
    }

    /** 쿼리 파라미터를 가린 JSON 문자열로. 없으면 null. 값이 하나면 문자열, 여럿이면 배열로 둔다. */
    public String maskParams(Map<String, String[]> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        ObjectNode node = objectMapper.createObjectNode();
        params.forEach((key, values) -> {
            if (SECRET_KEY.matcher(key).matches()) {
                node.put(key, MASK);
            } else if (values.length == 1) {
                node.put(key, values[0]);
            } else {
                ArrayNode array = node.putArray(key);
                for (String value : values) {
                    array.add(value);
                }
            }
        });
        return write(node);
    }

    public String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode mask(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (SECRET_KEY.matcher(name).matches()) {
                    object.put(name, MASK);
                } else {
                    mask(object.get(name));
                }
            }
        } else if (node instanceof ArrayNode array) {
            Iterator<JsonNode> elements = array.elements();
            while (elements.hasNext()) {
                mask(elements.next());
            }
        }
        return node;
    }
}
