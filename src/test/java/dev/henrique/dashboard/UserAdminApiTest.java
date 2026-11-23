package dev.henrique.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.henrique.collectorrunner.integration.CollectionRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class UserAdminApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean CollectionRunner runner;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        registry.add("dashboard.admin-email", () -> "admin@example.org");
        registry.add("dashboard.admin-password", () -> "administrator-test-password");
        registry.add("dashboard.jwt-secret", () -> "integration-test-secret-at-least-32-bytes");
    }

    private String adminToken;

    @BeforeEach
    void loginAdmin() throws Exception {
        adminToken = login("admin@example.org", "administrator-test-password");
    }

    @Test
    void adminCreatesUserAndOnlyAdminCanManageAccounts() throws Exception {
        MvcResult created = mvc.perform(post("/v1/admin/users").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"operator@example.org\",\"password\":\"operator-test-password\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn();

        org.junit.jupiter.api.Assertions.assertFalse(created.getResponse().getContentAsString().contains("password"));
        String operatorToken = login("operator@example.org", "operator-test-password");
        mvc.perform(get("/v1/admin/users").header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());

        long id = json.readTree(created.getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(post("/v1/admin/users/" + id + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"operator@example.org\",\"password\":\"operator-test-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(java.util.Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }
}
