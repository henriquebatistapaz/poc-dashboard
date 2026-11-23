package dev.henrique.collectorrunner.persistence;

import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface SocialPostRepository extends JpaRepository<SocialPostEntity, Long> {
    boolean existsBySourceAndExternalId(String source, String externalId);

    List<SocialPostEntity> findAllByOrderByPublishedAtDesc();

    @Query(value = """
            select p from SocialPostEntity p
             where (:source is null or lower(p.source) = lower(:source))
               and (:query is null or lower(coalesce(p.author, '')) like lower(concat('%', :query, '%'))
                    or lower(coalesce(p.text, '')) like lower(concat('%', :query, '%')))
               and (:from is null or p.publishedAt >= :from)
               and (:to is null or p.publishedAt <= :to)
            """,
            countQuery = """
            select count(p) from SocialPostEntity p
             where (:source is null or lower(p.source) = lower(:source))
               and (:query is null or lower(coalesce(p.author, '')) like lower(concat('%', :query, '%'))
                    or lower(coalesce(p.text, '')) like lower(concat('%', :query, '%')))
               and (:from is null or p.publishedAt >= :from)
               and (:to is null or p.publishedAt <= :to)
            """)
    Page<SocialPostEntity> search(@Param("source") String source, @Param("query") String query,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    long deleteBySourceAndExternalId(String source, String externalId);

    long deleteByCreatedAtBefore(Instant cutoff);
}
