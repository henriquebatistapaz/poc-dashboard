package com.example.collectorrunner.api;

import com.example.collectorrunner.application.SocialPostPersistenceService;
import com.example.collectorrunner.application.SocialPostPersistenceService.PostSearchPage;
import com.example.collectorrunner.domain.NormalizedSocialPost;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/posts")
@Tag(name = "Social posts", description = "Synthetic normalized social post ingestion")
@lombok.RequiredArgsConstructor
public class SocialPostController {
    private final SocialPostPersistenceService posts;
    @Value("${runner.admin-api-key:}")
    private String adminApiKey;

    @PostMapping
    @Operation(summary = "Accept a normalized post unless its source and external ID were seen")
    public ResponseEntity<PostIngestionResponse> ingest(@RequestBody NormalizedSocialPost post) {
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Post body is required");
        }
        try {
            List<NormalizedSocialPost> accepted = posts.acceptNew(List.of(post));
            String status = accepted.isEmpty() ? "duplicate" : "accepted";
            HttpStatus httpStatus = accepted.isEmpty() ? HttpStatus.OK : HttpStatus.CREATED;
            return ResponseEntity.status(httpStatus)
                    .body(new PostIngestionResponse(post.source(), post.externalId(), status));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping
    @Operation(summary = "List persisted normalized social posts, newest publication first")
    public List<NormalizedSocialPost> list() {
        return posts.findAll();
    }

    @GetMapping("/search")
    @Operation(summary = "Search posts with bounded pagination and optional source, text, and publication-date filters")
    public PostSearchPage search(
            @RequestParam(required = false) String source,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            return posts.search(source, query, from, to, page, size);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/{source}/{externalId}")
    @Operation(summary = "Remove one post by source and external ID using the configured runner admin key")
    public ResponseEntity<Void> delete(@PathVariable String source, @PathVariable String externalId,
            @Parameter(hidden = true) @RequestHeader(name = "X-Runner-Admin-Key", required = false) String suppliedKey) {
        if (!matchesAdminKey(suppliedKey)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try {
            if (!posts.delete(source, externalId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private boolean matchesAdminKey(String suppliedKey) {
        if (adminApiKey == null || adminApiKey.isBlank() || suppliedKey == null) return false;
        return MessageDigest.isEqual(adminApiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                suppliedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public record PostIngestionResponse(String source, String externalId, String status) {}
}
