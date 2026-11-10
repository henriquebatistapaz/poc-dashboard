package com.example.collectorrunner.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/** PostgreSQL writer. The caller supplies a migrated database; this class never creates tables. */
public final class CollectedItemWriter {
    private static final String INSERT = """
            INSERT INTO collected_item (source, external_id, title, link, published_at, collected_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (source, external_id) DO NOTHING
            """;
    private final DataSource dataSource;
    private final Clock clock;

    public CollectedItemWriter(DataSource dataSource) {
        this(dataSource, Clock.systemUTC());
    }

    public CollectedItemWriter(DataSource dataSource, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.clock = Objects.requireNonNull(clock);
    }

    /** One independent transaction per batch. First write wins; duplicates never overwrite stored content. */
    public WriteResult write(List<CollectedItemInput> items) throws SQLException {
        if (items == null) throw new IllegalArgumentException("items are required");
        List<CollectedItemInput> batch = List.copyOf(items);
        if (batch.size() > 1000) throw new IllegalArgumentException("batch exceeds 1000 items");
        if (batch.isEmpty()) return new WriteResult(0, 0);
        Instant collectedAt = clock.instant();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int inserted = 0;
                try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                    for (CollectedItemInput item : batch) {
                        statement.setString(1, item.source());
                        statement.setString(2, item.externalId());
                        statement.setString(3, item.title());
                        statement.setString(4, item.link());
                        statement.setTimestamp(5, item.publishedAt() == null ? null : Timestamp.from(item.publishedAt()));
                        statement.setTimestamp(6, Timestamp.from(collectedAt));
                        inserted += statement.executeUpdate();
                    }
                }
                connection.commit();
                return new WriteResult(inserted, batch.size() - inserted);
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollbackFailure) { exception.addSuppressed(rollbackFailure); }
                throw exception;
            }
        }
    }

    public record WriteResult(int inserted, int duplicates) { }
}
