package dev.henrique.dashboard.repository;

import dev.henrique.dashboard.domain.CollectedItem;
import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectedItemRepository extends JpaRepository<CollectedItem, Long>,
        JpaSpecificationExecutor<CollectedItem> {

    @Query("select i.externalId from CollectedItem i where i.source = :source and i.externalId in :ids")
    Set<String> findExistingExternalIds(@Param("source") String source, @Param("ids") Collection<String> ids);
}
