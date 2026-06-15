package DGU_AI_LAB.admin_be.error.exception;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.InfraOperationException.InfraStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

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
                    objectMap(objectMapper, source.get("progress")),
                    intOrNull(source, "k8s_status"),
                    text(source, "k8s_reason"),
                    text(source, "k8s_body")
            );
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(ObjectMapper mapper, JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return null;
        }
        return mapper.convertValue(node, LinkedHashMap.class);
    }

    public static InfraOperationException toException(
            ObjectMapper mapper,
            ErrorCode errorCode,
            InfraStep fallbackStep,
            Integer infraStatus,
            String fallbackMessage,
            String fallbackInfraError,
            String body
    ) {
        ParsedInfraError parsed = parse(mapper, body);
        InfraStep step = parseInfraStep(parsed != null ? parsed.step() : null, fallbackStep);
        String infraError = firstNonBlank(parsed != null ? parsed.error() : null, fallbackInfraError);
        String detail = firstNonBlank(parsed != null ? parsed.detail() : null, body);
        Map<String, Object> progress = parsed != null ? parsed.progress() : null;
        Integer k8sStatus = parsed != null ? parsed.k8sStatus() : null;
        String k8sReason = parsed != null ? parsed.k8sReason() : null;

        String message;
        if (parsed != null && parsed.error() != null && !parsed.error().isBlank()) {
            message = parsed.error();
        } else if (body != null && !body.isBlank()) {
            message = fallbackMessage + ": " + body;
        } else {
            message = fallbackMessage;
        }

        return new InfraOperationException(
                errorCode, message, step, infraStatus,
                infraError, detail, body,
                progress, k8sStatus, k8sReason
        );
    }

    private static InfraStep parseInfraStep(String step, InfraStep fallback) {
        if (step == null || step.isBlank()) {
            return fallback;
        }
        try {
            return InfraStep.valueOf(step.trim().replace('-', '_').toUpperCase());
        } catch (IllegalArgumentException e) {
            return InfraStep.UNKNOWN;
        }
    }

    private static String firstNonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
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

    private static Integer intOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && field.isNumber() ? field.intValue() : null;
    }

    public record ParsedInfraError(
            String step,
            String error,
            String detail,
            Map<String, Object> progress,
            Integer k8sStatus,
            String k8sReason,
            String k8sBody
    ) {}
}
