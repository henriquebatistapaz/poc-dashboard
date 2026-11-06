package com.henrique.dashboard.service;

import com.example.collectorrunner.integration.CollectionRunner;
import com.example.collectorrunner.integration.CollectionSource;
import com.henrique.collector.source.SourceAccessException;
import com.henrique.dashboard.domain.CollectionRun;
import com.henrique.dashboard.repository.CollectionRunRepository;
import java.sql.SQLException;
import java.util.List;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CollectionRunService {
    static final int MAX_PAGE_SIZE = 100;

    private final CollectionRunRepository repository;
    private final CollectionRunner runner;
    private final ExecutorService collectionExecutor;

    /** Saves a PENDING run and starts it in the background. The input is kept only in memory. */
    public CollectionRun start(CollectionSource source, String input) {
        CollectionRun run = repository.save(new CollectionRun(source.name(), Instant.now()));
        Long id = run.getId();
        collectionExecutor.submit(() -> execute(id, source, input));
        return run;
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
            run.markFailed(Instant.now(), safeMessage(exception));
        }
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
