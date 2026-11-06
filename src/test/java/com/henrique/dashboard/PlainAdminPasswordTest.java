package com.henrique.dashboard;

import com.example.collectorrunner.integration.CollectionRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "dashboard.admin-email=admin@example.org",
        "dashboard.admin-password=plain-test-password",
        "dashboard.jwt-secret=integration-test-secret-at-least-32-bytes"})
@AutoConfigureMockMvc
class PlainAdminPasswordTest {
    @Autowired MockMvc mvc;
    @MockBean CollectionRunner runner;

    @Test
    void acceptsPlainPasswordWhenNoHashIsConfigured() throws Exception {
        mvc.perform(post("/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.org\",\"password\":\"plain-test-password\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.org\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
}
