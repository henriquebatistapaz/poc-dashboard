package com.henrique.dashboard.service;

import com.example.collectorrunner.integration.CollectionRunner;
import com.example.collectorrunner.integration.CollectionSource;
import com.henrique.collector.source.SourceAccessException;
import com.henrique.dashboard.domain.CollectionRun;
import com.henrique.dashboard.domain.RunStatus;
import com.henrique.dashboard.repository.CollectionRunRepository;
import lombok.extern.slf4j.Slf4j;
import java.sql.SQLException;
import java.util.List;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionRunService {
    static final int MAX_PAGE_SIZE = 100;

    private final CollectionRunRepository repository;
    private final CollectionRunner runner;
    private final ExecutorService collectionExecutor;
    private final ScheduleInputCipher scheduleInputCipher;

    /** Saves a PENDING run and starts it in the background. The input is kept only in memory. */
    public CollectionRun start(CollectionSource source, String input) {
        CollectionRun run = repository.save(new CollectionRun(source.name(), Instant.now()));
        Long id = run.getId();
        collectionExecutor.submit(() -> execute(id, source, input));
        return run;
    }

    public CollectionRun schedule(CollectionSource source, String input, Instant scheduledAt) {
        if (!scheduleInputCipher.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Scheduling is not configured");
        }
        Instant now = Instant.now();
        if (scheduledAt == null || !scheduledAt.isAfter(now) || scheduledAt.isAfter(now.plusSeconds(365L * 24 * 3600))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduledAt must be within the next 365 days");
        }
        return repository.save(new CollectionRun(source.name(), now, scheduledAt, scheduleInputCipher.encrypt(input)));
    }

    public void dispatchDueSchedules() {
        Instant now = Instant.now();
        List<Long> due = repository.findDueScheduleIds(RunStatus.PENDING, now, PageRequest.of(0, 20));
        for (Long id : due) {
            if (repository.claimDueSchedule(id, RunStatus.PENDING, RunStatus.RUNNING, now) == 1) {
                collectionExecutor.submit(() -> executeScheduled(id));
            }
        }
    }

    private void executeScheduled(Long id) {
        CollectionRun run = repository.findById(id).orElseThrow();
        String input;
        try {
            input = scheduleInputCipher.decrypt(run.getEncryptedInput());
        } catch (RuntimeException exception) {
            run.markFailed(Instant.now(), "Scheduled collection input could not be decrypted");
            run.clearEncryptedInput();
            repository.save(run);
            return;
        }
        execute(id, CollectionSource.valueOf(run.getSource()), input);
    }

    public CollectionRun get(Long id) {
        return repository.findById(id).orElse(null);
    }

    public List<CollectionRun> list(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return repository.findAll(PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }

    void execute(Long id, CollectionSource source, String input) {
        CollectionRun run = repository.findById(id).orElseThrow();
        run.markRunning(Instant.now());
        repository.save(run);
        try {
            CollectionRunner.CollectionResult result = runner.run(source, input);
            run.markSucceeded(Instant.now(), result.fetched(), result.inserted(), result.duplicates());
        } catch (Exception exception) {
            // Upstream text can contain a secret feed URL, so only fixed messages are stored.
            // Logs carry only exception class names (never messages or inputs) to aid diagnosis.
            Throwable root = exception;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            log.warn("Collection run {} failed: {} (root cause: {})", id,
                    exception.getClass().getName(), root.getClass().getName());
            run.markFailed(Instant.now(), safeMessage(exception));
        }
        run.clearEncryptedInput();
        repository.save(run);
    }

    static String safeMessage(Exception exception) {
        if (exception instanceof SourceAccessException) {
            return "Source unavailable; check approval and runtime configuration";
        }
        if (exception instanceof IllegalArgumentException) {
            return "Invalid source input or unsupported item fields";
        }
        if (exception instanceof SQLException) {
            return "Collection storage is unavailable";
        }
        return "Upstream collection failed";
    }
}
