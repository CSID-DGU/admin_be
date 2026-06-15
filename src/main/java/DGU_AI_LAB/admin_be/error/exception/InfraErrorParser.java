package DGU_AI_LAB.admin_be.error.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class InfraErrorParser {
    private InfraErrorParser() {}

    public static ParsedInfraError parse(ObjectMapper objectMapper, String body) {
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode source = firstResultOrRoot(root);
            return new ParsedInfraError(
                    text(source, "step"),
                    text(source, "error"),
                    text(source, "detail"),
                    source.has("process") ? source.get("process").toString() : null
            );
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static JsonNode firstResultOrRoot(JsonNode root) {
        JsonNode results = root.get("results");
        if (results != null && results.isArray() && !results.isEmpty()) {
            return results.get(0);
        }
        return root;
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asText() : null;
    }

    public record ParsedInfraError(String step, String error, String detail, String process) {}
}
