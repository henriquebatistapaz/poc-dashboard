package com.henrique.dashboard.api;

import dev.henrique.collectorrunner.integration.CollectionSource;
import com.henrique.dashboard.domain.CollectionRun;
import com.henrique.dashboard.service.CollectionRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Collections", description = "Endpoints for triggering, scheduling and inspecting collection runs")
@RestController
@RequestMapping("/v1/collections")
public class CollectionController {
    private final CollectionRunService runs;

    public CollectionController(CollectionRunService runs) {
        this.runs = runs;
    }

    @Operation(summary = "Start a collection run in the background; returns the run to poll")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Collection run started",
                     content = @Content(schema = @Schema(implementation = CollectionRunResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid source or input"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - write permission required")
    })
    @PostMapping
    public ResponseEntity<CollectionRunResponse> start(@RequestBody CollectionRequest request) {
        if (request == null || request.source() == null || request.input() == null || request.input().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source and input are required");
        }
        validateSourceInput(request.source(), request.input());
        CollectionRun run = runs.start(request.source(), request.input());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(run.getId()).toUri();
        return ResponseEntity.accepted().location(location).body(toResponse(run));
    }

    @Operation(summary = "Schedule one collection for a future time")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Collection run scheduled",
                     content = @Content(schema = @Schema(implementation = CollectionRunResponse.class))),
        @ApiResponse(responseCode = "400", description = "Missing or invalid scheduledAt, source or input"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - write permission required")
    })
    @PostMapping("/schedules")
    public ResponseEntity<CollectionRunResponse> schedule(@RequestBody ScheduleRequest request) {
        if (request == null || request.source() == null || request.input() == null || request.input().isBlank()
                || request.scheduledAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source, input and scheduledAt are required");
        }
        validateSourceInput(request.source(), request.input());
        CollectionRun run = runs.schedule(request.source(), request.input(), request.scheduledAt());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().replacePath("/v1/collections/{id}")
                .buildAndExpand(run.getId()).toUri();
        return ResponseEntity.accepted().location(location).body(toResponse(run));
    }

    private static void validateSourceInput(CollectionSource source, String input) {
        String value = input.trim();
        int max = switch (source) {
            case WIKIPEDIA, GITLAB -> 200;
            case LOBSTERS -> 100;
            default -> Integer.MAX_VALUE;
        };
        boolean openRestSource = source == CollectionSource.WIKIPEDIA
                || source == CollectionSource.GITLAB || source == CollectionSource.LOBSTERS;
        boolean invalidLobstersTag = source == CollectionSource.LOBSTERS
                && !value.equals("hottest") && !value.matches("[A-Za-z0-9]{1,100}");
        if (value.length() > max || (openRestSource && value.codePoints().anyMatch(Character::isISOControl))
                || invalidLobstersTag) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid source input");
        }
    }

    @Operation(summary = "Status and outcome of one collection run")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Collection run details",
                     content = @Content(schema = @Schema(implementation = CollectionRunResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Collection run not found")
    })
    @GetMapping("/{id}")
    public CollectionRunResponse get(@Parameter(description = "Collection run ID") @PathVariable Long id) {
        CollectionRun run = runs.get(id);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection run not found");
        }
        return toResponse(run);
    }

    @Operation(summary = "Latest collection runs, newest first")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of runs",
                     content = @Content(array = @ArraySchema(schema = @Schema(implementation = CollectionRunResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    public List<CollectionRunResponse> list(@Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
                                            @Parameter(description = "Page size (1-100)") @RequestParam(defaultValue = "20") int size) {
        return runs.list(page, size).stream().map(this::toResponse).toList();
    }

    private CollectionRunResponse toResponse(CollectionRun run) {
        return new CollectionRunResponse(run.getId(), run.getSource(), run.getStatus().name(), run.getCreatedAt(), run.getScheduledAt(),
                run.getStartedAt(), run.getFinishedAt(), runs.inputForDisplay(run), run.getFetched(), run.getInserted(),
                run.getDuplicates(), run.getErrorMessage());
    }

    public record CollectionRequest(
        @Schema(description = "Source provider", example = "RSS")
        CollectionSource source,
        @Schema(description = "Input parameter (URL, tag, query)", example = "https://news.ycombinator.com/rss")
        String input
    ) {
        @Override public String toString() { return "CollectionRequest[source=" + source + ", input=REDACTED]"; }
    }

    public record ScheduleRequest(
        @Schema(description = "Source provider", example = "RSS")
        CollectionSource source,
        @Schema(description = "Input parameter", example = "https://news.ycombinator.com/rss")
        String input,
        @Schema(description = "Scheduled execution timestamp (future ISO-8601)")
        Instant scheduledAt
    ) {
        @Override public String toString() { return "ScheduleRequest[source=" + source + ", input=REDACTED, scheduledAt=" + scheduledAt + "]"; }
    }

    public record CollectionRunResponse(
        @Schema(description = "Run identifier", example = "1")
        Long id,
        @Schema(description = "Source provider", example = "RSS")
        String source,
        @Schema(description = "Execution status", example = "SUCCEEDED")
        String status,
        @Schema(description = "Creation timestamp")
        Instant createdAt,
        @Schema(description = "Scheduled execution timestamp")
        Instant scheduledAt,
        @Schema(description = "Start timestamp")
        Instant startedAt,
        @Schema(description = "End timestamp")
        Instant finishedAt,
        @Schema(description = "Masked or sanitized input")
        String input,
        @Schema(description = "Number of fetched items", example = "10")
        Integer fetched,
        @Schema(description = "Number of newly inserted items", example = "8")
        Integer inserted,
        @Schema(description = "Number of duplicates skipped", example = "2")
        Integer duplicates,
        @Schema(description = "Error message on failure")
        String errorMessage
    ) { }
}
