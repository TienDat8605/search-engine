package com.searchengine.api.dto;

import java.time.Instant;
import java.util.List;

public record AnalyticsResponse(
        Instant generatedAt,
        long totalQueries,
        long queriesLast24Hours,
        long totalDocuments,
        List<TopQuery> topQueries
) {
    public record TopQuery(String query, long hits) {
    }
}
