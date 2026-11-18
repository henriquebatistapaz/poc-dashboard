package com.henrique.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henrique.dashboard.domain.CollectedItem;
import com.henrique.dashboard.domain.ItemRemoval;
import com.henrique.dashboard.repository.CollectedItemRepository;
import com.henrique.dashboard.repository.ItemRemovalRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "dashboard.api-key=reader-key")
@AutoConfigureMockMvc
class ItemRemovalApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CollectedItemRepository items;
    @Autowired ItemRemovalRepository removals;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        registry.add("dashboard.admin-email", () -> "admin@example.org");
        registry.add("dashboard.admin-password-hash", () -> new BCryptPasswordEncoder(4).encode("test-password"));
        registry.add("dashboard.jwt-secret", () -> "integration-test-secret-at-least-32-bytes");
    }

    private String adminToken() throws Exception {
        var response = mvc.perform(post("/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.org\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private long seedItem() {
        return items.save(new CollectedItem("rss", "https://a.example/" + System.nanoTime(), "Título",
                "https://a.example/1", null, Instant.parse("2026-11-03T10:00:00Z"))).getId();
    }

    @BeforeEach
    void clean() {
        removals.deleteAll();
        items.deleteAll();
    }

    @Test
    void requiresAuthentication() throws Exception {
        long id = seedItem();
        mvc.perform(delete("/v1/items/" + id).contentType("application/json").content("{\"reason\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(items.existsById(id)).isTrue();
    }

    @Test
    void operatorCannotRemoveItem() throws Exception {
        long id = seedItem();
        mvc.perform(post("/v1/admin/users").header("Authorization", "Bearer " + adminToken())
                .contentType("application/json")
                .content("{\"email\":\"op-removal@example.org\",\"password\":\"op-test-password\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated());
        var opRes = mvc.perform(post("/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"op-removal@example.org\",\"password\":\"op-test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        String opToken = json.readTree(opRes.getResponse().getContentAsString()).path("accessToken").asText();

        mvc.perform(delete("/v1/items/" + id).header("Authorization", "Bearer " + opToken)
                .contentType("application/json").content("{\"reason\":\"tentativa sem permissao\"}"))
                .andExpect(status().isForbidden());
        assertThat(items.existsById(id)).isTrue();
        assertThat(removals.findAll()).isEmpty();
    }

    @Test
    void readOnlyApiKeyCannotRemove() throws Exception {
        long id = seedItem();
        mvc.perform(delete("/v1/items/" + id).header("X-API-Key", "reader-key")
                .contentType("application/json").content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        assertThat(items.existsById(id)).isTrue();
    }

    @Test
    void reasonIsRequired() throws Exception {
        long id = seedItem();
        mvc.perform(delete("/v1/items/" + id).header("Authorization", "Bearer " + adminToken())
                .contentType("application/json").content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        assertThat(items.existsById(id)).isTrue();
    }

    @Test
    void unknownItemIsNotFound() throws Exception {
        mvc.perform(delete("/v1/items/999999").header("Authorization", "Bearer " + adminToken())
                .contentType("application/json").content("{\"reason\":\"pedido do titular\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminRemovesItemAndAuditRecordIsWritten() throws Exception {
        long id = seedItem();
        mvc.perform(delete("/v1/items/" + id).header("Authorization", "Bearer " + adminToken())
                .contentType("application/json").content("{\"reason\":\"  pedido do titular  \"}"))
                .andExpect(status().isNoContent());

        assertThat(items.existsById(id)).isFalse();
        assertThat(removals.findAll()).hasSize(1);
        ItemRemoval audit = removals.findAll().get(0);
        assertThat(audit.getReason()).isEqualTo("pedido do titular");
        assertThat(audit.getRemovedBy()).isEqualTo("admin@example.org");
        assertThat(audit.getSource()).isEqualTo("rss");
    }
}
