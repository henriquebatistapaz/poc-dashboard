package dev.henrique.collectorrunner.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FixtureJsonTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsObjectAndRequiredTextInstantAndArray() {
        var root = FixtureJson.read(mapper,
                "{\"name\":\"sample\",\"created\":\"2026-01-01T00:00:00Z\",\"values\":[]}");

        assertEquals("sample", FixtureJson.requiredText(root, "name"));
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), FixtureJson.requiredInstant(root, "created"));
        assertEquals(true, FixtureJson.requiredArray(root, "values").isArray());
    }

    @Test
    void rejectsEmptyOrInvalidJsonAndNonObjectRoot() {
        assertThrows(IllegalArgumentException.class, () -> FixtureJson.read(mapper, ""));
        assertThrows(IllegalArgumentException.class, () -> FixtureJson.read(mapper, "not-json"));
        assertThrows(IllegalArgumentException.class, () -> FixtureJson.read(mapper, "null"));
        assertThrows(IllegalArgumentException.class, () -> FixtureJson.read(mapper, "[]"));
    }

    @Test
    void rejectsMissingBlankAndNonTextFieldsAndInvalidArraysOrInstants() {
        var root = FixtureJson.read(mapper, "{\"blank\":\" \",\"number\":1,\"invalidInstant\":\"later\",\"object\":{}}");

        assertThrows(IllegalArgumentException.class, () -> FixtureJson.requiredText(root, "missing"));
        assertThrows(IllegalArgumentException.class, () -> FixtureJson.requiredText(root, "blank"));
        assertThrows(IllegalArgumentException.class, () -> FixtureJson.requiredText(root, "number"));
        assertThrows(IllegalArgumentException.class, () -> FixtureJson.requiredInstant(root, "missing"));
        assertThrows(IllegalArgumentException.class, () -> FixtureJson.requiredInstant(root, "invalidInstant"));
        assertThrows(IllegalArgumentException.class, () -> FixtureJson.requiredArray(root, "missing"));
        assertThrows(IllegalArgumentException.class, () -> FixtureJson.requiredArray(root, "object"));
    }
}
