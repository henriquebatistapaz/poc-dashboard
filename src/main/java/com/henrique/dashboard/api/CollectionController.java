package com.henrique.dashboard.api;

import com.example.collectorrunner.integration.CollectionRunner;
import com.example.collectorrunner.integration.CollectionSource;
import com.henrique.collector.source.SourceAccessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/collections")
@RequiredArgsConstructor
public class CollectionController {
    private final CollectionRunner runner;

    @PostMapping
    @Operation(summary = "Run one synchronous collection and persist new items")
    @SecurityRequirement(name = "bearerAuth")
    public CollectionRunner.CollectionResult collect(@RequestBody CollectionRequest request) {
        try {
            return runner.run(request.source(), request.input());
        } catch (SourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Source unavailable; check approval and runtime configuration");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid source input or unsupported item fields");
        } catch (SQLException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Collection storage is unavailable");
        } catch (RuntimeException exception) {
            // Upstream exception text can contain a secret Google Alerts URL. Do not reflect it.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream collection failed");
        }
    }

    public record CollectionRequest(CollectionSource source, String input) {
        @Override public String toString() { return "CollectionRequest[source=" + source + ", input=REDACTED]"; }
    }
}
