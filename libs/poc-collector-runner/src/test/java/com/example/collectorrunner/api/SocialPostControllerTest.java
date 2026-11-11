package com.example.collectorrunner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SocialPostControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void acceptsFirstPostAndMarksRepeatAsDuplicate() throws Exception {
        String body = """
                {"source":"demo","externalId":"post-1","author":"sample","text":"hello",
                 "publishedAt":"2026-01-01T00:00:00Z","permalink":"https://example.test/post-1"}
                """;

        mockMvc.perform(post("/v1/posts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("accepted"));
        mockMvc.perform(post("/v1/posts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("duplicate"));
    }

    @Test
    void listsPersistedPosts() throws Exception {
        String body = """
                {"source":"list-test","externalId":"post-2","author":"sample","text":"persisted",
                 "publishedAt":"2026-01-02T00:00:00Z","permalink":"https://example.test/post-2"}
                """;
        mockMvc.perform(post("/v1/posts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.externalId == 'post-2')]").exists());
    }

    @Test
    void exposesOpenApiDocument() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/v1/posts'].post").exists())
                .andExpect(jsonPath("$.paths['/v1/posts'].get").exists());
    }

    @Test
    void rejectsPostsWithoutRequiredDeduplicationIdentity() throws Exception {
        mockMvc.perform(post("/v1/posts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"demo\",\"externalId\":\" \",\"author\":\"sample\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/v1/posts").contentType(MediaType.APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest());
    }
}
