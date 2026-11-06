package com.henrique.dashboard.api;

import com.henrique.dashboard.domain.CollectedItem;
import com.henrique.dashboard.repository.CollectedItemRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@io.swagger.v3.oas.annotations.security.SecurityRequirements({
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "apiKey"),
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
})
@RestController
@RequestMapping("/v1/items")
public class ItemController {
    static final int MAX_PAGE_SIZE = 100;

    private final CollectedItemRepository repository;

    public ItemController(CollectedItemRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ItemPageResponse list(@RequestParam(required = false) String source,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(required = false) Instant from,
                                 @RequestParam(required = false) Instant to,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before or equal to to");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Specification<CollectedItem> spec = filter(source, q, from, to);
        Page<CollectedItem> result = repository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "collectedAt", "id")));
        List<ItemResponse> items = result.getContent().stream().map(ItemResponse::from).toList();
        return new ItemPageResponse(items, result.getTotalElements(), safePage, safeSize);
    }

    @GetMapping("/{id}")
    public ItemResponse get(@PathVariable Long id) {
        return repository.findById(id)
                .map(ItemResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }

    static Specification<CollectedItem> filter(String source, String q, Instant from, Instant to) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (source != null && !source.isBlank()) {
                predicates.add(cb.equal(root.get("source"), source.trim()));
            }
            if (q != null && !q.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%"));
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
