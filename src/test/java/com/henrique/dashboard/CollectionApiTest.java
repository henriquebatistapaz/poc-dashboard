package com.henrique.dashboard;

import com.example.collectorrunner.integration.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henrique.collector.source.SourceAccessException;
import java.sql.SQLException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "dashboard.api-key=reader-key")
@AutoConfigureMockMvc
class CollectionApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;
    @MockitoBean CollectionRunner runner;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        registry.add("dashboard.admin-email", () -> "admin@example.org");
        registry.add("dashboard.admin-password-hash", () -> new BCryptPasswordEncoder(4).encode("test-password"));
        registry.add("dashboard.jwt-secret", () -> "integration-test-secret-at-least-32-bytes");
    }

    private String login() throws Exception {
        var response = mvc.perform(post("/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.org\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresIn").value(900)).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).path("accessToken").asText();
    }

    @Test
    void loginPermitsManualCollectionAndReadButApiKeyCannotWrite() throws Exception {
        when(runner.run(CollectionSource.GOOGLE_NEWS, "java")).thenReturn(new CollectionRunner.CollectionResult(CollectionSource.GOOGLE_NEWS, 2, 1, 1));
        String body = "{\"source\":\"GOOGLE_NEWS\",\"input\":\"java\"}";
        mvc.perform(post("/v1/collections").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/collections").header("X-API-Key", "reader-key").contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        String token = login();
        mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token).contentType("application/json").content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/v1/items").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void removedGoogleAlertsSourceCannotStartNewCollection() throws Exception {
        String token = login();
        mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"source\":\"GOOGLE_ALERTS\",\"input\":\"java\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(runner);
    }

    @ParameterizedTest
    @CsvSource({"WIKIPEDIA, sistema solar", "GITLAB, spring boot", "LOBSTERS, hottest"})
    void acceptsOpenSourcesWithValidInputs(String source, String input) throws Exception {
        String token = login();
        mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(json.writeValueAsString(new CollectionControllerBody(source, input))))
                .andExpect(status().isAccepted());
        verify(runner, timeout(2_000)).run(CollectionSource.valueOf(source), input);
    }

    @ParameterizedTest
    @CsvSource({"WIKIPEDIA, blank", "WIKIPEDIA, too-long", "GITLAB, too-long", "LOBSTERS, blank", "LOBSTERS, invalid-tag", "LOBSTERS, too-long"})
    void rejectsInvalidOpenSourceInputsBeforeStartingRun(String source, String invalidKind) throws Exception {
        String input = switch (invalidKind) {
            case "blank" -> "   ";
            case "too-long" -> "x".repeat("LOBSTERS".equals(source) ? 101 : 201);
            case "invalid-tag" -> "tag-with-hyphen";
            default -> throw new IllegalArgumentException(invalidKind);
        };
        String token = login();
        mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(json.writeValueAsString(new CollectionControllerBody(source, input))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(runner);
    }

    @Test
    void rejectsBadLoginAndInvalidOrExpiredTokens() throws Exception {
        mvc.perform(post("/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.org\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/items").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        var claims = JwtClaimsSet.builder().issuer("poc-dashboard").subject("admin@example.org")
                .issuedAt(Instant.now().minusSeconds(3600)).expiresAt(Instant.now().minusSeconds(120))
                .claim("scope", "items:read collections:write").build();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/v1/items").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongIssuerAndTokensWithoutCollectionScope() throws Exception {
        var now = Instant.now();
        var wrongIssuer = JwtClaimsSet.builder().issuer("other-app").subject("admin@example.org")
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("scope", "items:read collections:write").build();
        String invalid = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), wrongIssuer)).getTokenValue();
        mvc.perform(get("/v1/items").header("Authorization", "Bearer " + invalid)).andExpect(status().isUnauthorized());
        var readOnly = JwtClaimsSet.builder().issuer("poc-dashboard").subject("reader@example.org")
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("scope", "items:read").build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), readOnly)).getTokenValue();
        mvc.perform(get("/v1/items").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"source\":\"GOOGLE_NEWS\",\"input\":\"java\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(runner);
    }

    @Test
    void startDoesNotExposeSecretFeedUrlInResponse() throws Exception {
        // Failure mapping now happens per run (see CollectionRunApiTest and CollectionRunServiceTest).
        String token = login();
        String secret = "https://example.org/feed?token=secret";
        String body = json.writeValueAsString(new CollectionControllerBody("RSS", secret));
        doThrow(new IllegalStateException(secret)).when(runner).run(CollectionSource.RSS, secret);
        var result = mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body)).andExpect(status().isAccepted()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains(secret));
    }

    record CollectionControllerBody(String source, String input) { }
}
