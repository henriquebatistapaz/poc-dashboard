package com.henrique.dashboard.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.henrique.dashboard.domain.CollectedItem;
import com.henrique.dashboard.service.ItemQueryService;
import com.henrique.dashboard.service.ItemRemovalService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class ItemControllerUnitTest {

    private ItemQueryService queryService;
    private ItemRemovalService removalService;
    private ItemController controller;

    @BeforeEach
    void setUp() {
        queryService = mock(ItemQueryService.class);
        removalService = mock(ItemRemovalService.class);
        controller = new ItemController(queryService, removalService);
    }

    @Test
    @DisplayName("Throws BAD_REQUEST when from timestamp is after to timestamp")
    void rejectsInvalidDateRange() {
        Instant now = Instant.now();
        Instant future = now.plusSeconds(3600);

        assertThatThrownBy(() -> controller.list("RSS", null, future, now, 0, 20))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("from must be before or equal to to");
    }

    @Test
    @DisplayName("Lists items mapping into response DTOs")
    void listsItemsSuccessfully() {
        CollectedItem item = new CollectedItem("rss", "id-1", "Hello", "https://example.com/1", null, Instant.now());

        when(queryService.listItems(eq("RSS"), eq("query"), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageImpl<>(List.of(item)));

        ItemPageResponse response = controller.list("RSS", "query", null, null, 0, 20);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).title()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("Throws NOT_FOUND when item does not exist")
    void getThrowsNotFound() {
        when(queryService.getItem(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(42L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Item not found");
    }

    @Test
    @DisplayName("Delegates removal to ItemRemovalService with authenticated user")
    void delegatesRemoval() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin-user");

        controller.remove(5L, new ItemRemovalRequest("spam"), auth);

        verify(removalService).remove(5L, "spam", "admin-user");
    }
}
