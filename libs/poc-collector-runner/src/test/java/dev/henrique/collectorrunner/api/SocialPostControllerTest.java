package dev.henrique.collectorrunner.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "runner.admin-api-key=runner-test-admin-key")
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

    @Test
    void searchesPostsBySourceTextDatesAndPage() throws Exception {
        mockMvc.perform(post("/v1/posts").contentType(MediaType.APPLICATION_JSON).content("""
                {"source":"search-alpha","externalId":"one","author":"reader","text":"Java feeds",
                 "publishedAt":"2026-01-10T00:00:00Z","permalink":"https://example.test/one"}
                """)).andExpect(status().isCreated());
        mockMvc.perform(post("/v1/posts").contentType(MediaType.APPLICATION_JSON).content("""
                {"source":"search-alpha","externalId":"two","author":"reader","text":"Java tests",
                 "publishedAt":"2026-01-11T00:00:00Z","permalink":"https://example.test/two"}
                """)).andExpect(status().isCreated());
        mockMvc.perform(post("/v1/posts").contentType(MediaType.APPLICATION_JSON).content("""
                {"source":"search-beta","externalId":"three","author":"reader","text":"Java feeds",
                 "publishedAt":"2026-01-11T00:00:00Z","permalink":"https://example.test/three"}
                """)).andExpect(status().isCreated());

        mockMvc.perform(get("/v1/posts/search")
                        .param("source", "SEARCH-ALPHA")
                        .param("q", "java")
                        .param("from", "2026-01-10T00:00:00Z")
                        .param("to", "2026-01-11T00:00:00Z")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/v1/posts/search").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/posts/search").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresConfiguredAdminKeyForTakedown() throws Exception {
        mockMvc.perform(post("/v1/posts").contentType(MediaType.APPLICATION_JSON).content("""
                {"source":"takedown","externalId":"remove-me","author":"sample","text":"synthetic",
                 "publishedAt":"2026-01-01T00:00:00Z","permalink":"https://example.test/remove-me"}
                """)).andExpect(status().isCreated());

        mockMvc.perform(delete("/v1/posts/takedown/remove-me")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/v1/posts/takedown/remove-me").header("X-Runner-Admin-Key", "wrong-key"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/v1/posts/takedown/remove-me").header("X-Runner-Admin-Key", "runner-test-admin-key"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/posts/search").param("source", "takedown"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
}
