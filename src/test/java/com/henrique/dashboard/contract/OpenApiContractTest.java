package com.henrique.dashboard.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "dashboard.api-key=contract-test-key")
@AutoConfigureMockMvc
class OpenApiContractTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("AC-02: OpenAPI specification /v3/api-docs is valid and documents all endpoints")
    void openApiDocumentsAllRequiredEndpoints() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.has("openapi")).isTrue();
        assertThat(root.get("openapi").asText()).startsWith("3.");

        JsonNode paths = root.get("paths");
        assertThat(paths).isNotNull();

        List<String> expectedPaths = List.of(
                "/v1/auth/login",
                "/v1/collections",
                "/v1/collections/schedules",
                "/v1/collections/{id}",
                "/v1/items",
                "/v1/items/{id}",
                "/v1/admin/users",
                "/v1/admin/users/{id}/deactivate"
        );

        for (String expectedPath : expectedPaths) {
            assertThat(paths.has(expectedPath))
                    .withFailMessage("OpenAPI missing documented path: %s", expectedPath)
                    .isTrue();
        }

        JsonNode securitySchemes = root.path("components").path("securitySchemes");
        assertThat(securitySchemes.has("apiKey")).isTrue();
        assertThat(securitySchemes.has("bearerAuth")).isTrue();
    }
}
