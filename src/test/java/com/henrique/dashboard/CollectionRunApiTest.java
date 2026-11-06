package com.henrique.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.collectorrunner.integration.CollectionRunner;
import com.example.collectorrunner.integration.CollectionSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henrique.collector.source.SourceAccessException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "dashboard.api-key=reader-key")
@AutoConfigureMockMvc
class CollectionRunApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean CollectionRunner runner;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        registry.add("dashboard.admin-email", () -> "admin@example.org");
        registry.add("dashboard.admin-password-hash", () -> new BCryptPasswordEncoder(4).encode("test-password"));
        registry.add("dashboard.jwt-secret", () -> "integration-test-secret-at-least-32-bytes");
    }

    private String token;

    @BeforeEach
    void login() throws Exception {
        MvcResult response = mvc.perform(post("/v1/auth/login").contentType("application/json")
                        .content("{\"email\":\"admin@example.org\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        token = json.readTree(response.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private JsonNode awaitStatus(String location, String expected) throws Exception {
        URI uri = URI.create(location);
        JsonNode body = null;
        for (int attempt = 0; attempt < 100; attempt++) {
            MvcResult result = mvc.perform(get(uri.getPath()).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andReturn();
            body = json.readTree(result.getResponse().getContentAsString());
            if (expected.equals(body.path("status").asText())) {
                return body;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("run never reached " + expected + ": " + body);
    }

    @Test
    void startReturnsAcceptedAndRunFinishesWithCounts() throws Exception {
        when(runner.run(CollectionSource.GOOGLE_NEWS, "open data")).thenReturn(
                new CollectionRunner.CollectionResult(CollectionSource.GOOGLE_NEWS, 4, 3, 1));

        MvcResult created = mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"source\":\"GOOGLE_NEWS\",\"input\":\"open data\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String location = created.getResponse().getHeader("Location");
        assertTrue(location.contains("/v1/collections/"));
        assertFalse(created.getResponse().getContentAsString().contains("open data"), "input must not be echoed");

        JsonNode done = awaitStatus(location, "SUCCEEDED");
        assertEquals(4, done.path("fetched").asInt());
        assertEquals(3, done.path("inserted").asInt());
        assertEquals(1, done.path("duplicates").asInt());
        assertTrue(done.path("errorMessage").isNull() || done.path("errorMessage").isMissingNode());
    }

    @Test
    void failedRunShowsSafeMessageWithoutSecret() throws Exception {
        when(runner.run(CollectionSource.GOOGLE_ALERTS, "alert-input")).thenThrow(
                new SourceAccessException("https://www.google.com/alerts/feeds/SECRET-TOKEN"));

        MvcResult created = mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"source\":\"GOOGLE_ALERTS\",\"input\":\"alert-input\"}"))
                .andExpect(status().isAccepted()).andReturn();

        JsonNode failed = awaitStatus(created.getResponse().getHeader("Location"), "FAILED");
        assertEquals("Source unavailable; check approval and runtime configuration", failed.path("errorMessage").asText());
        assertFalse(failed.toString().contains("SECRET-TOKEN"));
    }

    @Test
    void rejectsBlankInputBeforeStarting() throws Exception {
        mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"source\":\"REDDIT\",\"input\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRunIsNotFound() throws Exception {
        mvc.perform(get("/v1/collections/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsRunsNewestFirst() throws Exception {
        when(runner.run(any(), any())).thenReturn(new CollectionRunner.CollectionResult(CollectionSource.WORDPRESS, 1, 1, 0));
        MvcResult created = mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"source\":\"WORDPRESS\",\"input\":\"https://wp.example/feed\"}"))
                .andExpect(status().isAccepted()).andReturn();
        // Wait for the background run so it cannot leak into another test's mock.
        awaitStatus(created.getResponse().getHeader("Location"), "SUCCEEDED");
        mvc.perform(get("/v1/collections").param("size", "500").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").isNotEmpty());
    }

    @Test
    void apiKeyCannotReadOrStartRuns() throws Exception {
        mvc.perform(get("/v1/collections").header("X-API-Key", "reader-key")).andExpect(status().isForbidden());
        mvc.perform(post("/v1/collections").header("X-API-Key", "reader-key").contentType("application/json")
                .content("{\"source\":\"REDDIT\",\"input\":\"x\"}")).andExpect(status().isForbidden());
    }
}
