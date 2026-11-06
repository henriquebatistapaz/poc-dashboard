package com.henrique.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "dashboard.api-key=")
@AutoConfigureMockMvc
class UnconfiguredApiKeyTest {
    @Autowired MockMvc mvc;
    @Test
    void unconfiguredLoginIsUnavailable() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/auth/login")
                .contentType("application/json").content("{\"email\":\"any\",\"password\":\"any\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void emptyConfigurationNeverAuthenticates() throws Exception {
        mvc.perform(get("/v1/items").header("X-API-Key", "")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/items").header("X-API-Key", "anything")).andExpect(status().isUnauthorized());
    }
}
