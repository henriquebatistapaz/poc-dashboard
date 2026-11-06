package com.henrique.dashboard;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.henrique.dashboard.domain.CollectedItem;
import com.henrique.dashboard.repository.CollectedItemRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ItemControllerTest {
    @Autowired MockMvc mvc;
    @Autowired CollectedItemRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.save(new CollectedItem("wordpress", "https://a.example/1", "Open data in Brazil",
                "https://a.example/1", Instant.parse("2026-11-01T10:00:00Z"), Instant.parse("2026-11-02T10:00:00Z")));
        repository.save(new CollectedItem("google-news", "https://b.example/2", "Tecnologia abertas",
                "https://b.example/2", null, Instant.parse("2026-11-03T10:00:00Z")));
    }

    @Test
    void listsAllItemsNewestFirst() throws Exception {
        mvc.perform(get("/v1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].source").value("google-news"));
    }

    @Test
    void filtersBySourceAndTitle() throws Exception {
        mvc.perform(get("/v1/items").param("source", "wordpress"))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/v1/items").param("q", "ABERTAS"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].source").value("google-news"));
    }

    @Test
    void clampsPageSize() throws Exception {
        mvc.perform(get("/v1/items").param("size", "500"))
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void returnsItemByIdOrNotFound() throws Exception {
        Long id = repository.findAll().get(0).getId();
        mvc.perform(get("/v1/items/" + id)).andExpect(status().isOk());
        mvc.perform(get("/v1/items/999999")).andExpect(status().isNotFound());
    }
}
