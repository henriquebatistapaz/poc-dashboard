package com.henrique.dashboard.repository;

import com.henrique.dashboard.domain.CollectionRun;
import com.henrique.dashboard.domain.RunStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionRunRepository extends JpaRepository<CollectionRun, Long> {
    List<CollectionRun> findByStatusIn(List<RunStatus> statuses);
}
