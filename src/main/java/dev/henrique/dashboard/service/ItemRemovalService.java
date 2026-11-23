package dev.henrique.dashboard.service;

import dev.henrique.dashboard.domain.CollectedItem;
import dev.henrique.dashboard.domain.ItemRemoval;
import dev.henrique.dashboard.repository.CollectedItemRepository;
import dev.henrique.dashboard.repository.ItemRemovalRepository;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Remove um item sob pedido e grava o motivo e o autor na mesma transação. */
@Service
public class ItemRemovalService {
    static final int MAX_REASON_LENGTH = 500;

    private final CollectedItemRepository items;
    private final ItemRemovalRepository removals;

    public ItemRemovalService(CollectedItemRepository items, ItemRemovalRepository removals) {
        this.items = items;
        this.removals = removals;
    }

    @Transactional
    public void remove(long id, String reason, String actor) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required");
        }
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason must be at most 500 characters");
        }
        CollectedItem item = items.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        Instant now = Instant.now();
        removals.save(new ItemRemoval(item.getSource(), item.getExternalId(), item.getLink(), trimmed, actor, now));
        items.delete(item);
    }
}
