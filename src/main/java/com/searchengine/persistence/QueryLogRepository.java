package com.searchengine.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QueryLogRepository extends JpaRepository<QueryLogEntity, Long> {

    long countByCreatedAtAfter(Instant after);

    @Query("""
            select q.queryText as queryText, count(q) as hits
            from QueryLogEntity q
            group by q.queryText
            order by count(q) desc
            """)
    List<QueryHitView> findTopQueries(Pageable pageable);
}
