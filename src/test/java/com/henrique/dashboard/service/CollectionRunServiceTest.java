package com.henrique.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.collectorrunner.integration.CollectionRunner;
import com.example.collectorrunner.integration.CollectionSource;
import com.henrique.collector.source.SourceAccessException;
import com.henrique.dashboard.domain.CollectionRun;
import com.henrique.dashboard.domain.RunStatus;
import com.henrique.dashboard.repository.CollectionRunRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CollectionRunServiceTest {
    @Mock CollectionRunRepository repository;
    @Mock CollectionRunner runner;
    @Mock ExecutorService executor;

    CollectionRunService service;
    CollectionRun stored;

    @BeforeEach
    void setUp() {
        service = new CollectionRunService(repository, runner, executor);
        // Persist as the database would: assign an id and keep one shared instance.
        lenient().when(repository.save(any(CollectionRun.class))).thenAnswer(invocation -> {
            CollectionRun run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "id", 1L);
            stored = run;
            return run;
        });
        lenient().when(repository.findById(1L)).thenAnswer(invocation -> Optional.ofNullable(stored));
        // Run submitted tasks inline so the test is deterministic.
        lenient().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).submit(any(Runnable.class));
    }

    @Test
    void startSavesPendingRunAndExecutesInBackground() throws Exception {
        when(runner.run(CollectionSource.GOOGLE_NEWS, "java"))
                .thenReturn(new CollectionRunner.CollectionResult(CollectionSource.GOOGLE_NEWS, 5, 3, 2));

        CollectionRun returned = service.start(CollectionSource.GOOGLE_NEWS, "java");

        assertThat(returned.getId()).isEqualTo(1L);
        assertThat(stored.getStatus()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(stored.getFetched()).isEqualTo(5);
        assertThat(stored.getInserted()).isEqualTo(3);
        assertThat(stored.getDuplicates()).isEqualTo(2);
        assertThat(stored.getStartedAt()).isNotNull();
        assertThat(stored.getFinishedAt()).isNotNull();
    }

    @Test
    void inputIsNeverPersisted() throws Exception {
        when(runner.run(CollectionSource.REDDIT, "secret-query"))
                .thenReturn(new CollectionRunner.CollectionResult(CollectionSource.REDDIT, 0, 0, 0));

        service.start(CollectionSource.REDDIT, "secret-query");

        assertThat(stored.getSource()).isEqualTo("REDDIT");
        assertThat(stored.toString()).doesNotContain("secret-query");
        assertThat(stored.getErrorMessage()).isNull();
    }

    @Test
    void sourceAccessFailureStoresFixedMessageWithoutSecretUrl() throws Exception {
        when(runner.run(any(), any())).thenThrow(new SourceAccessException("https://alerts.example/feed?token=SECRET"));

        service.start(CollectionSource.GOOGLE_ALERTS, "ignored");

        assertThat(stored.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(stored.getErrorMessage()).isEqualTo("Source unavailable; check approval and runtime configuration");
        assertThat(stored.getErrorMessage()).doesNotContain("SECRET");
    }

    @Test
    void storageFailureMapsToStorageMessage() throws Exception {
        when(runner.run(any(), any())).thenThrow(new SQLException("connection refused"));

        service.start(CollectionSource.WORDPRESS, "https://wp.example/feed");

        assertThat(stored.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(stored.getErrorMessage()).isEqualTo("Collection storage is unavailable");
    }

    @Test
    void invalidInputMapsToInputMessage() throws Exception {
        when(runner.run(any(), any())).thenThrow(new IllegalArgumentException("bad input"));

        service.start(CollectionSource.WORDPRESS, "not a url");

        assertThat(stored.getErrorMessage()).isEqualTo("Invalid source input or unsupported item fields");
    }

    @Test
    void unexpectedFailureMapsToGenericMessage() throws Exception {
        when(runner.run(any(), any())).thenThrow(new IllegalStateException("boom https://x?token=SECRET"));

        service.start(CollectionSource.WORDPRESS, "https://wp.example/feed");

        assertThat(stored.getErrorMessage()).isEqualTo("Upstream collection failed");
        assertThat(stored.getErrorMessage()).doesNotContain("SECRET");
    }

    @Test
    void recoveryMarksInterruptedRunsAsFailed() {
        CollectionRun pending = new CollectionRun("WORDPRESS", java.time.Instant.now());
        CollectionRun running = new CollectionRun("REDDIT", java.time.Instant.now());
        running.markRunning(java.time.Instant.now());
        when(repository.findByStatusIn(List.of(RunStatus.PENDING, RunStatus.RUNNING)))
                .thenReturn(List.of(pending, running));

        new CollectionRunRecovery(repository).run(null);

        assertThat(pending.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(running.getErrorMessage()).isEqualTo("Interrupted by application restart");
        verify(repository).saveAll(List.of(pending, running));
    }

    @Test
    void getReturnsNullWhenMissing() throws Exception {
        when(repository.findById(42L)).thenReturn(Optional.empty());
        assertThat(service.get(42L)).isNull();
        verify(runner, never()).run(any(), any());
    }
}
