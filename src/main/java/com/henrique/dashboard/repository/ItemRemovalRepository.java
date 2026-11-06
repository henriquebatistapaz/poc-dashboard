package com.henrique.dashboard.repository;

import com.henrique.dashboard.domain.ItemRemoval;
import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRemovalRepository extends JpaRepository<ItemRemoval, Long> {

    /** External IDs of this source that were removed by an administrator. */
    @Query("select r.externalId from ItemRemoval r where r.source = :source and r.externalId in :ids")
    Set<String> findRemovedExternalIds(@Param("source") String source, @Param("ids") Collection<String> ids);
}
