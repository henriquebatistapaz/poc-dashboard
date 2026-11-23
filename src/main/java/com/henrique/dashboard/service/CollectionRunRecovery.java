package com.henrique.dashboard.service;

import dev.henrique.collectorrunner.integration.CollectionSource;
import com.henrique.dashboard.domain.CollectionRun;
import com.henrique.dashboard.domain.RunStatus;
import com.henrique.dashboard.repository.CollectionRunRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Recovers interrupted immediate runs and preserves scheduled runs for dispatch. */
@Component
@RequiredArgsConstructor
public class CollectionRunRecovery implements ApplicationRunner {
    private final CollectionRunRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        List<CollectionRun> interrupted = repository.findByStatusIn(List.of(RunStatus.PENDING, RunStatus.RUNNING));
        Instant now = Instant.now();
        interrupted.forEach(run -> {
            if (run.getScheduledAt() != null && !isSupportedSource(run.getSource())) {
                run.markFailed(now, "Scheduled collection source is no longer supported");
                run.clearEncryptedInput();
            } else if (run.getScheduledAt() != null && run.getStatus() == RunStatus.RUNNING && run.getEncryptedInput() != null) {
                run.retryScheduleAfterRestart();
            } else if (run.getScheduledAt() == null) {
                run.markFailed(now, "Interrupted by application restart");
            }
        });
        repository.saveAll(interrupted);
    }

    private boolean isSupportedSource(String source) {
        try {
            CollectionSource.valueOf(source);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
