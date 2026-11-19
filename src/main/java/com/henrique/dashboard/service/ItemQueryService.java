package com.henrique.dashboard.service;

import com.henrique.dashboard.domain.CollectedItem;
import com.henrique.dashboard.repository.CollectedItemRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ItemQueryService {
    public static final int MAX_PAGE_SIZE = 100;

    private final CollectedItemRepository repository;

    public ItemQueryService(CollectedItemRepository repository) {
        this.repository = repository;
    }

    public Page<CollectedItem> listItems(String source, String q, Instant from, Instant to, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Specification<CollectedItem> spec = filter(source, q, from, to);
        return repository.findAll(spec, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "collectedAt", "id")));
    }

    public Optional<CollectedItem> getItem(Long id) {
        return repository.findById(id);
    }

    public static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return source.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Specification<CollectedItem> filter(String source, String q, Instant from, Instant to) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            String normalized = normalizeSource(source);
            if (normalized != null) {
                predicates.add(cb.equal(root.get("source"), normalized));
            }
            if (q != null && !q.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + q.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("collectedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("collectedAt"), to));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
