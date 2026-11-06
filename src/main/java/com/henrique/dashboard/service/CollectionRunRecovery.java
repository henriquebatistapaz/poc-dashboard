package com.henrique.dashboard.service;

import com.henrique.dashboard.domain.CollectionRun;
import com.henrique.dashboard.domain.RunStatus;
import com.henrique.dashboard.repository.CollectionRunRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Runs left PENDING or RUNNING by a restart cannot finish, so they are marked FAILED. */
@Component
@RequiredArgsConstructor
public class CollectionRunRecovery implements ApplicationRunner {
    private final CollectionRunRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        List<CollectionRun> interrupted = repository.findByStatusIn(List.of(RunStatus.PENDING, RunStatus.RUNNING));
        Instant now = Instant.now();
        interrupted.forEach(run -> run.markFailed(now, "Interrupted by application restart"));
        repository.saveAll(interrupted);
    }
}
