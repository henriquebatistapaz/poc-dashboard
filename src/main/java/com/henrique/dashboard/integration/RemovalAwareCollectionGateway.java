package com.henrique.dashboard.integration;

import dev.henrique.collectorrunner.integration.CollectedItemInput;
import dev.henrique.collectorrunner.integration.CollectionGateway;
import dev.henrique.collectorrunner.integration.CollectionSource;
import com.henrique.dashboard.repository.ItemRemovalRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Descarta, antes da gravação, itens que um administrador removeu (tabela item_removal).
 * Sem isso, uma nova coleta traria o item de volta.
 */
public final class RemovalAwareCollectionGateway implements CollectionGateway {
    private final CollectionGateway delegate;
    private final ItemRemovalRepository removals;

    public RemovalAwareCollectionGateway(CollectionGateway delegate, ItemRemovalRepository removals) {
        this.delegate = delegate;
        this.removals = removals;
    }

    @Override
    public List<CollectedItemInput> collect(CollectionSource source, String input) {
        List<CollectedItemInput> fetched = delegate.collect(source, input);
        if (fetched.isEmpty()) {
            return fetched;
        }
        Map<String, List<String>> idsBySource = new LinkedHashMap<>();
        for (CollectedItemInput item : fetched) {
            idsBySource.computeIfAbsent(item.source(), k -> new ArrayList<>()).add(item.externalId());
        }
        Map<String, Set<String>> removedBySource = new LinkedHashMap<>();
        idsBySource.forEach((src, ids) -> removedBySource.put(src, removals.findRemovedExternalIds(src, ids)));
        List<CollectedItemInput> kept = new ArrayList<>(fetched.size());
        for (CollectedItemInput item : fetched) {
            if (!removedBySource.get(item.source()).contains(item.externalId())) {
                kept.add(item);
            }
        }
        return kept;
    }
}
