package com.searchengine.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.searchengine.api.dto.AnalyticsResponse;
import com.searchengine.persistence.DocumentRepository;
import com.searchengine.persistence.QueryLogRepository;

@Service
public class AnalyticsService {

    private final QueryLogRepository queryLogRepository;
    private final DocumentRepository documentRepository;

    public AnalyticsService(QueryLogRepository queryLogRepository, DocumentRepository documentRepository) {
        this.queryLogRepository = queryLogRepository;
        this.documentRepository = documentRepository;
    }

    public AnalyticsResponse getSummary() {
        Instant now = Instant.now();
        Instant since = now.minus(24, ChronoUnit.HOURS);

        long totalQueries = queryLogRepository.count();
        long queriesLast24Hours = queryLogRepository.countByCreatedAtAfter(since);
        long totalDocuments = documentRepository.count();

        List<AnalyticsResponse.TopQuery> topQueries = queryLogRepository.findTopQueries(PageRequest.of(0, 10))
                .stream()
                .map(row -> new AnalyticsResponse.TopQuery(row.getQueryText(), row.getHits()))
                .toList();

        return new AnalyticsResponse(now, totalQueries, queriesLast24Hours, totalDocuments, topQueries);
    }
}
