package dev.henrique.dashboard.api;

import dev.henrique.dashboard.domain.CollectedItem;
import dev.henrique.dashboard.service.ItemQueryService;
import dev.henrique.dashboard.service.ItemRemovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SecurityRequirements({
    @SecurityRequirement(name = "apiKey"),
    @SecurityRequirement(name = "bearerAuth")
})
@RestController
@RequestMapping("/v1/items")
public class ItemController {
    public static final int MAX_PAGE_SIZE = ItemQueryService.MAX_PAGE_SIZE;

    private final ItemQueryService queryService;
    private final ItemRemovalService removal;

    public ItemController(ItemQueryService queryService, ItemRemovalService removal) {
        this.queryService = queryService;
        this.removal = removal;
    }

    @Operation(summary = "List collected items with optional filtering and pagination")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of collected items",
                     content = @Content(schema = @Schema(implementation = ItemPageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid filter dates or parameters"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid credentials")
    })
    @GetMapping
    public ItemPageResponse list(@Parameter(description = "Source slug or enum name") @RequestParam(required = false) String source,
                                 @Parameter(description = "Search term in title") @RequestParam(required = false) String q,
                                 @Parameter(description = "Start timestamp (ISO-8601)") @RequestParam(required = false) Instant from,
                                 @Parameter(description = "End timestamp (ISO-8601)") @RequestParam(required = false) Instant to,
                                 @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
                                 @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "20") int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before or equal to to");
        }
        Page<CollectedItem> result = queryService.listItems(source, q, from, to, page, size);
        List<ItemResponse> items = result.getContent().stream().map(ItemResponse::from).toList();
        return new ItemPageResponse(items, result.getTotalElements(), Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    @Operation(summary = "Get a single collected item by its identifier")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Item found",
                     content = @Content(schema = @Schema(implementation = ItemResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Item not found")
    })
    @GetMapping("/{id}")
    public ItemResponse get(@Parameter(description = "Item ID") @PathVariable Long id) {
        return queryService.getItem(id)
                .map(ItemResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
    }

    @Operation(summary = "Remove a collected item with an audit reason (Admin only)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Item removed successfully"),
        @ApiResponse(responseCode = "400", description = "Missing or empty reason"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role"),
        @ApiResponse(responseCode = "404", description = "Item not found")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@Parameter(description = "Item ID to remove") @PathVariable Long id,
                       @RequestBody(required = false) ItemRemovalRequest body,
                       Authentication authentication) {
        String reason = body == null ? null : body.reason();
        removal.remove(id, reason, authentication != null ? authentication.getName() : "anonymous");
    }

    public static String normalizeSource(String source) {
        return ItemQueryService.normalizeSource(source);
    }
}
