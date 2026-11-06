package com.henrique.dashboard.repository;

import com.henrique.dashboard.domain.CollectedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CollectedItemRepository extends JpaRepository<CollectedItem, Long>,
        JpaSpecificationExecutor<CollectedItem> {
}
