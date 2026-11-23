package dev.henrique.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import dev.henrique.collectorrunner.integration.CollectionRunner;
import dev.henrique.collectorrunner.integration.CollectionSource;
import dev.henrique.dashboard.domain.CollectionRun;
import dev.henrique.dashboard.domain.RunStatus;
import dev.henrique.dashboard.repository.CollectionRunRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CollectionRunServiceUnitTest {

    private CollectionRunRepository repository;
    private CollectionRunner runner;
    private ScheduleInputCipher cipher;
    private ExecutorService executor;
    private CollectionRunService service;

    @BeforeEach
    void setUp() {
        repository = mock(CollectionRunRepository.class);
        runner = mock(CollectionRunner.class);
        cipher = mock(ScheduleInputCipher.class);
        executor = mock(ExecutorService.class);
        service = new CollectionRunService(repository, runner, executor, cipher);
    }

    @Test
    void scheduleThrowsWhenCipherNotConfigured() {
        when(cipher.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.schedule(CollectionSource.RSS, "https://example.com/rss", Instant.now().plusSeconds(3600)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Scheduling is not configured");
    }

    @Test
    void scheduleThrowsWhenScheduledAtOutOfRange() {
        when(cipher.isConfigured()).thenReturn(true);

        // In the past
        assertThatThrownBy(() -> service.schedule(CollectionSource.RSS, "https://example.com/rss", Instant.now().minusSeconds(10)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("scheduledAt must be within the next 365 days");

        // Beyond 365 days
        assertThatThrownBy(() -> service.schedule(CollectionSource.RSS, "https://example.com/rss", Instant.now().plusSeconds(366L * 24 * 3600)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("scheduledAt must be within the next 365 days");

        // Null
        assertThatThrownBy(() -> service.schedule(CollectionSource.RSS, "https://example.com/rss", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void executeScheduledFailsGracefullyWhenDecryptionThrows() {
        CollectionRun run = new CollectionRun("RSS", Instant.now(), Instant.now(), "corrupted-cipher");
        when(repository.findById(10L)).thenReturn(Optional.of(run));
        when(cipher.decrypt("corrupted-cipher")).thenThrow(new RuntimeException("Decryption failed"));

        when(repository.claimDueSchedule(eq(10L), eq(RunStatus.PENDING), eq(RunStatus.RUNNING), any())).thenReturn(1);
        when(repository.findDueScheduleIds(eq(RunStatus.PENDING), any(), any())).thenReturn(java.util.List.of(10L));
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executor).submit(any(Runnable.class));

        service.dispatchDueSchedules();

        assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("Scheduled collection input could not be decrypted");
        verify(repository, atLeastOnce()).save(run);

        // Branch where claimDueSchedule returns 0 (already claimed by other node)
        when(repository.claimDueSchedule(eq(10L), eq(RunStatus.PENDING), eq(RunStatus.RUNNING), any())).thenReturn(0);
        service.dispatchDueSchedules();
    }

    @Test
    void executeWithRootCauseLoop() throws Exception {
        CollectionRun run = new CollectionRun("RSS", Instant.now(), null, "input");
        when(repository.findById(5L)).thenReturn(Optional.of(run));

        Exception cause = new Exception("inner");
        Exception exWithSelfCause = new RuntimeException("outer", cause);
        when(runner.run(any(), any())).thenThrow(exWithSelfCause);

        service.execute(5L, CollectionSource.RSS, "https://example.com");

        assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void itemRemovalServiceValidation() {
        ItemRemovalService removalService = new ItemRemovalService(mock(dev.henrique.dashboard.repository.CollectedItemRepository.class), mock(dev.henrique.dashboard.repository.ItemRemovalRepository.class));

        assertThatThrownBy(() -> removalService.remove(1L, null, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("reason is required");

        assertThatThrownBy(() -> removalService.remove(1L, "   ", "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("reason is required");

        assertThatThrownBy(() -> removalService.remove(1L, "a".repeat(501), "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at most 500 characters");
    }
}
