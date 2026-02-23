package com.searchengine.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEventEntity, Long> {

    /**
     * Returns click counts grouped by URL for a given normalized query,
     * restricted to the specified URLs and time window.
     */
    @Query("""
            SELECT c.url AS url, COUNT(c) AS clicks
            FROM ClickEventEntity c
            WHERE c.queryText = :query
              AND c.url IN :urls
              AND c.createdAt > :since
            GROUP BY c.url
            """)
    List<UrlClickCount> countClicksForQueryAndUrls(
            @Param("query") String query,
            @Param("urls") List<String> urls,
            @Param("since") Instant since
    );

    interface UrlClickCount {
        String getUrl();
        long getClicks();
    }
}
