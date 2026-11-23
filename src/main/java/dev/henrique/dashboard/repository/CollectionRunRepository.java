package dev.henrique.dashboard.repository;

import dev.henrique.dashboard.domain.CollectionRun;
import dev.henrique.dashboard.domain.RunStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CollectionRunRepository extends JpaRepository<CollectionRun, Long> {
    List<CollectionRun> findByStatusIn(List<RunStatus> statuses);

    @Query("select run.id from CollectionRun run where run.status = :pending and run.scheduledAt <= :now order by run.scheduledAt")
    List<Long> findDueScheduleIds(@Param("pending") RunStatus pending, @Param("now") Instant now,
                                  Pageable page);

    @Modifying
    @Transactional
    @Query("update CollectionRun run set run.status = :running, run.startedAt = :now where run.id = :id and run.status = :pending and run.scheduledAt <= :now")
    int claimDueSchedule(@Param("id") Long id, @Param("pending") RunStatus pending,
                         @Param("running") RunStatus running, @Param("now") Instant now);
}
