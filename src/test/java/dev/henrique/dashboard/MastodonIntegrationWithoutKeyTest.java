package dev.henrique.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import dev.henrique.dashboard.security.JwtConfiguration;

/** Sem DASHBOARD_SCHEDULE_KEY, toda a integração Mastodon responde 503 (AC-10). */
@SpringBootTest
@AutoConfigureMockMvc
class MastodonIntegrationWithoutKeyTest {
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("dashboard.schedule-key", () -> "");
        registry.add("dashboard.jwt-secret", () -> "integration-test-secret-at-least-32-bytes");
        registry.add("dashboard.admin-email", () -> "admin@example.org");
        registry.add("dashboard.admin-password-hash", () -> "$2a$04$abcdefghijklmnopqrstuuJ5eYn0JH2o7rE7s1b6Q0qS0Zf0bQe1C");
    }

    private String token() {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(JwtConfiguration.ISSUER).subject("nokey@example.org")
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("scope", "collections:write").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void everyIntegrationEndpointRespondsServiceUnavailableWithoutKey() throws Exception {
        String bearer = token();
        mvc.perform(post("/v1/integrations/mastodon/connect").header("Authorization", "Bearer " + bearer)
                .contentType("application/json").content("{\"instance\":\"https://mastodon.social\"}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/v1/integrations/mastodon/connection").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(delete("/v1/integrations/mastodon/connection").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/v1/integrations/mastodon/search").header("Authorization", "Bearer " + bearer)
                .contentType("application/json").content("{\"query\":\"java\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
