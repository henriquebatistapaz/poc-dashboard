package com.henrique.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.henrique.dashboard.domain.CollectionRun;
import com.henrique.dashboard.domain.RunStatus;
import com.henrique.dashboard.repository.CollectionRunRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class CollectionRunRecoveryUnitTest {

    @Test
    void recoversInterruptedScheduledAndImmediateRuns() {
        CollectionRunRepository repo = mock(CollectionRunRepository.class);

        // 1. Scheduled with unsupported source
        CollectionRun unsupported = new CollectionRun("UNSUPPORTED_XYZ", Instant.now(), Instant.now(), "cipher");

        // 2. Scheduled RUNNING with encrypted input (retries)
        CollectionRun scheduledRunning = new CollectionRun("RSS", Instant.now(), Instant.now(), "cipher");
        scheduledRunning.markRunning(Instant.now());

        // 3. Scheduled RUNNING with null encrypted input
        CollectionRun scheduledRunningNoInput = new CollectionRun("RSS", Instant.now(), Instant.now(), null);
        scheduledRunningNoInput.markRunning(Instant.now());

        // 4. Scheduled PENDING (no-op branch)
        CollectionRun scheduledPending = new CollectionRun("RSS", Instant.now(), Instant.now(), "cipher");

        // 5. Immediate run (scheduledAt == null)
        CollectionRun immediate = new CollectionRun("RSS", Instant.now(), null, "cipher");

        when(repo.findByStatusIn(any())).thenReturn(List.of(
                unsupported, scheduledRunning, scheduledRunningNoInput, scheduledPending, immediate
        ));

        CollectionRunRecovery recovery = new CollectionRunRecovery(repo);
        recovery.run(new DefaultApplicationArguments(new String[0]));

        assertThat(unsupported.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(scheduledRunning.getStatus()).isEqualTo(RunStatus.PENDING);
        assertThat(immediate.getStatus()).isEqualTo(RunStatus.FAILED);
        verify(repo).saveAll(any());
    }
}
