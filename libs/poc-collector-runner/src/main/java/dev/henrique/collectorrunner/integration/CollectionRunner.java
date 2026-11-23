package dev.henrique.collectorrunner.integration;

import java.sql.SQLException;
import lombok.RequiredArgsConstructor;

/** Synchronous manual collection. No scheduler or message broker is introduced. */
@RequiredArgsConstructor
public final class CollectionRunner {
    private final CollectionGateway gateway;
    private final CollectedItemWriter writer;

    public CollectionResult run(CollectionSource source, String input) throws SQLException {
        if (source == null) throw new IllegalArgumentException("source is required");
        if (input == null || input.isBlank() || input.length() > 4096) {
            throw new IllegalArgumentException("input must contain 1 to 4096 characters");
        }
        var items = gateway.collect(source, input.trim());
        var result = writer.write(items);
        return new CollectionResult(source, items.size(), result.inserted(), result.duplicates());
    }

    public record CollectionResult(CollectionSource source, int fetched, int inserted, int duplicates) { }
}
