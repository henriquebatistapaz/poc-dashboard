package dev.henrique.collectorrunner.application;

import dev.henrique.collectorrunner.domain.NormalizedSocialPost;
import dev.henrique.collectorrunner.persistence.SocialPostEntity;
import dev.henrique.collectorrunner.persistence.SocialPostRepository;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@lombok.RequiredArgsConstructor
public class SocialPostPersistenceService {
    private final SocialPostRepository repository;


    @Transactional
    public List<NormalizedSocialPost> acceptNew(List<NormalizedSocialPost> posts) {
        if (posts == null) throw new IllegalArgumentException("Posts are required");
        for (NormalizedSocialPost post : posts) validate(post);

        List<NormalizedSocialPost> accepted = new ArrayList<>();
        for (NormalizedSocialPost post : posts) {
            String source = post.source().trim();
            String externalId = post.externalId().trim();
            if (!repository.existsBySourceAndExternalId(source, externalId)) {
                accepted.add(repository.save(new SocialPostEntity(post)).toDomain());
            }
        }
        return List.copyOf(accepted);
    }

    @Transactional(readOnly = true)
    public List<NormalizedSocialPost> findAll() {
        return repository.findAllByOrderByPublishedAtDesc().stream()
                .map(SocialPostEntity::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public PostSearchPage search(String source, String query, Instant from, Instant to, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("Page must be zero or greater");
        if (size < 1 || size > 100) throw new IllegalArgumentException("Page size must be between 1 and 100");
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("From must be before or equal to to");
        }
        String normalizedSource = blankToNull(source);
        String normalizedQuery = blankToNull(query);
        if (normalizedSource != null && normalizedSource.length() > 100) {
            throw new IllegalArgumentException("Source filter exceeds 100 characters");
        }
        if (normalizedQuery != null && normalizedQuery.length() > 200) {
            throw new IllegalArgumentException("Query filter exceeds 200 characters");
        }
        var result = repository.search(normalizedSource, normalizedQuery, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("createdAt"))));
        return new PostSearchPage(result.getContent().stream().map(SocialPostEntity::toDomain).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public boolean delete(String source, String externalId) {
        if (source == null || source.isBlank() || externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("Source and externalId are required");
        }
        return repository.deleteBySourceAndExternalId(source.trim(), externalId.trim()) > 0;
    }

    @Transactional
    public long deleteCreatedBefore(Instant cutoff) {
        if (cutoff == null) throw new IllegalArgumentException("Cutoff is required");
        return repository.deleteByCreatedAtBefore(cutoff);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record PostSearchPage(List<NormalizedSocialPost> items, int page, int size,
            long totalElements, int totalPages) {}

    private void validate(NormalizedSocialPost post) {
        if (post == null) throw new IllegalArgumentException("Post is required");
        if (post.source() == null || post.source().isBlank()) {
            throw new IllegalArgumentException("Post source is required for deduplication");
        }
        if (post.source().trim().length() > 100) throw new IllegalArgumentException("Post source exceeds 100 characters");
        if (post.externalId() == null || post.externalId().isBlank()) {
            throw new IllegalArgumentException("Post externalId is required for deduplication");
        }
        if (post.externalId().trim().length() > 500) {
            throw new IllegalArgumentException("Post externalId exceeds 500 characters");
        }
    }
}
