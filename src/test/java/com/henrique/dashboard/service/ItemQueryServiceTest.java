package com.henrique.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.henrique.dashboard.domain.CollectedItem;
import com.henrique.dashboard.repository.CollectedItemRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class ItemQueryServiceTest {

    private CollectedItemRepository repository;
    private ItemQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(CollectedItemRepository.class);
        service = new ItemQueryService(repository);
    }

    @Test
    @DisplayName("Normalizes source strings according to specification")
    void normalizesSource() {
        assertThat(ItemQueryService.normalizeSource(null)).isNull();
        assertThat(ItemQueryService.normalizeSource("  ")).isNull();
        assertThat(ItemQueryService.normalizeSource("GOOGLE_NEWS")).isEqualTo("google-news");
        assertThat(ItemQueryService.normalizeSource(" rss ")).isEqualTo("rss");
    }

    @Test
    @DisplayName("Queries items with bounded pagination")
    void listItems() {
        Page<CollectedItem> emptyPage = new PageImpl<>(List.of());
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        Page<CollectedItem> result = service.listItems("RSS", "test", Instant.now().minusSeconds(3600), Instant.now(), -1, 500);

        assertThat(result).isNotNull();
        verify(repository).findAll(any(Specification.class), any(Pageable.class));

        // Test with null and blank q
        service.listItems(null, null, null, null, 0, 10);
        service.listItems(null, "   ", null, null, 0, 10);
    }

    @Test
    @DisplayName("Fetches single item by id")
    void getItem() {
        CollectedItem item = new CollectedItem("rss", "ext-1", "Test", "https://example.com", null, Instant.now());
        when(repository.findById(10L)).thenReturn(Optional.of(item));

        Optional<CollectedItem> result = service.getItem(10L);

        assertThat(result).contains(item);
        assertThat(service.getItem(99L)).isEmpty();
    }
}
