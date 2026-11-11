package com.henrique.dashboard.api;

import com.example.collectorrunner.integration.CollectionSource;
import com.henrique.dashboard.domain.CollectionRun;
import com.henrique.dashboard.service.CollectionRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

@RestController
@RequestMapping("/v1/collections")
public class CollectionController {
    private final CollectionRunService runs;

    public CollectionController(CollectionRunService runs) {
        this.runs = runs;
    }

    @PostMapping
    @Operation(summary = "Start a collection in the background; returns the run to poll")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CollectionRunResponse> start(@RequestBody CollectionRequest request) {
        if (request == null || request.source() == null || request.input() == null || request.input().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source and input are required");
        }
        CollectionRun run = runs.start(request.source(), request.input());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(run.getId()).toUri();
        return ResponseEntity.accepted().location(location).body(CollectionRunResponse.from(run));
    }

    @PostMapping("/schedules")
    @Operation(summary = "Schedule one collection for a future time")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CollectionRunResponse> schedule(@RequestBody ScheduleRequest request) {
        if (request == null || request.source() == null || request.input() == null || request.input().isBlank()
                || request.scheduledAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source, input and scheduledAt are required");
        }
        CollectionRun run = runs.schedule(request.source(), request.input(), request.scheduledAt());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().replacePath("/v1/collections/{id}")
                .buildAndExpand(run.getId()).toUri();
        return ResponseEntity.accepted().location(location).body(CollectionRunResponse.from(run));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Status and outcome of one collection run")
    @SecurityRequirement(name = "bearerAuth")
    public CollectionRunResponse get(@PathVariable Long id) {
        CollectionRun run = runs.get(id);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection run not found");
        }
        return CollectionRunResponse.from(run);
    }

    @GetMapping
    @Operation(summary = "Latest collection runs, newest first")
    @SecurityRequirement(name = "bearerAuth")
    public List<CollectionRunResponse> list(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return runs.list(page, size).stream().map(CollectionRunResponse::from).toList();
    }

    public record CollectionRequest(CollectionSource source, String input) {
        @Override public String toString() { return "CollectionRequest[source=" + source + ", input=REDACTED]"; }
    }

    public record ScheduleRequest(CollectionSource source, String input, Instant scheduledAt) {
        @Override public String toString() { return "ScheduleRequest[source=" + source + ", input=REDACTED, scheduledAt=" + scheduledAt + "]"; }
    }

    public record CollectionRunResponse(Long id, String source, String status, Instant createdAt, Instant scheduledAt, Instant startedAt,
                                        Instant finishedAt, Integer fetched, Integer inserted, Integer duplicates,
                                        String errorMessage) {
        static CollectionRunResponse from(CollectionRun run) {
            return new CollectionRunResponse(run.getId(), run.getSource(), run.getStatus().name(), run.getCreatedAt(), run.getScheduledAt(),
                    run.getStartedAt(), run.getFinishedAt(), run.getFetched(), run.getInserted(), run.getDuplicates(),
                    run.getErrorMessage());
        }
    }
}
