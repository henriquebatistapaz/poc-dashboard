package dev.henrique.collectorrunner.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

final class FixtureJson {
    private FixtureJson() {}

    static JsonNode read(ObjectMapper mapper, String fixture) {
        try {
            JsonNode root = mapper.readTree(fixture);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Fixture root must be a JSON object");
            }
            return root;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Fixture must contain valid JSON", exception);
        }
    }

    static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Required non-blank field is missing: " + field);
        }
        return value.asText();
    }

    static Instant requiredInstant(JsonNode node, String field) {
        String value = requiredText(node, field);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Field must be an ISO-8601 instant: " + field, exception);
        }
    }

    static JsonNode requiredArray(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isArray()) {
            throw new IllegalArgumentException("Required array is missing: " + field);
        }
        return value;
    }
}
