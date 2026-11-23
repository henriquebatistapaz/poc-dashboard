package dev.henrique.collectorrunner.integration;

import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollectionRunnerTest {
    @Test
    void returnsCountsAndValidatesBeforeCollecting() throws Exception {
        var gateway = mock(CollectionGateway.class);
        var writer = mock(CollectedItemWriter.class);
        var runner = new CollectionRunner(gateway, writer);
        var items = List.of(new CollectedItemInput("demo", "id", null, "https://example.org", null));
        when(gateway.collect(CollectionSource.RSS, "https://example.org/feed")).thenReturn(items);
        when(writer.write(items)).thenReturn(new CollectedItemWriter.WriteResult(1, 0));
        assertEquals(new CollectionRunner.CollectionResult(CollectionSource.RSS, 1, 1, 0),
                runner.run(CollectionSource.RSS, " https://example.org/feed "));
        assertThrows(IllegalArgumentException.class, () -> runner.run(null, "input"));
        assertThrows(IllegalArgumentException.class, () -> runner.run(CollectionSource.RSS, " "));
        assertThrows(IllegalArgumentException.class, () -> runner.run(CollectionSource.RSS, "x".repeat(4097)));
        verify(gateway, times(1)).collect(any(), any());
    }

    @Test
    void propagatesStorageFailureInsteadOfReportingSuccess() throws Exception {
        var gateway = mock(CollectionGateway.class);
        var writer = mock(CollectedItemWriter.class);
        var runner = new CollectionRunner(gateway, writer);
        when(gateway.collect(any(), any())).thenReturn(List.of());
        when(writer.write(any())).thenThrow(new SQLException("offline"));
        assertThrows(SQLException.class, () -> runner.run(CollectionSource.GOOGLE_NEWS, "java"));
    }
}
