package com.henrique.dashboard.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.henrique.collectorrunner.integration.CollectedItemInput;
import dev.henrique.collectorrunner.integration.CollectionGateway;
import dev.henrique.collectorrunner.integration.CollectionSource;
import com.henrique.dashboard.repository.ItemRemovalRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RemovalAwareCollectionGatewayTest {

    private static CollectedItemInput item(String source, String externalId) {
        return new CollectedItemInput(source, externalId, "t", "https://x.example/" + externalId, null);
    }

    @Test
    void dropsItemsRemovedByAdministrator() {
        var fetched = List.of(item("rss", "a"), item("rss", "b"), item("rss", "c"));
        CollectionGateway delegate = (source, input) -> fetched;
        ItemRemovalRepository removals = mock(ItemRemovalRepository.class);
        when(removals.findRemovedExternalIds(anyString(), anyCollection())).thenReturn(Set.of("b"));

        var kept = new RemovalAwareCollectionGateway(delegate, removals).collect(CollectionSource.RSS, "https://x");

        assertThat(kept).extracting(CollectedItemInput::externalId).containsExactly("a", "c");
    }

    @Test
    void keepsEverythingWhenNothingWasRemoved() {
        var fetched = List.of(item("rss", "a"));
        CollectionGateway delegate = (source, input) -> fetched;
        ItemRemovalRepository removals = mock(ItemRemovalRepository.class);
        when(removals.findRemovedExternalIds(anyString(), anyCollection())).thenReturn(Set.of());

        assertThat(new RemovalAwareCollectionGateway(delegate, removals).collect(CollectionSource.RSS, "https://x"))
                .hasSize(1);
    }

    @Test
    void skipsDatabaseWhenNothingFetched() {
        CollectionGateway delegate = (source, input) -> List.of();
        ItemRemovalRepository removals = mock(ItemRemovalRepository.class);

        assertThat(new RemovalAwareCollectionGateway(delegate, removals).collect(CollectionSource.RSS, "https://x"))
                .isEmpty();
        verify(removals, never()).findRemovedExternalIds(anyString(), anyCollection());
    }
}
