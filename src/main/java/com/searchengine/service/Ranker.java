package com.searchengine.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.searchengine.api.dto.SearchItem;
import com.searchengine.domain.ProviderSearchResult;

@Component
public class Ranker {

    public List<SearchItem> rank(String query, List<ProviderSearchResult> results, int limit) {
        List<String> terms = Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(term -> !term.isBlank())
                .toList();

        return results.stream()
                .map(result -> {
                    double relevance = relevanceScore(terms, result.title() + " " + result.snippet());
                    double freshness = freshnessScore(result.publishedAt());
                    double score = relevance + result.sourceQuality() + freshness;

                    return new SearchItem(
                            result.questionId(),
                            result.title(),
                            result.source().name(),
                            result.tags(),
                            result.questionScore(),
                            result.answered(),
                            result.acceptedAnswerId() != null,
                            result.snippet(),
                            result.url(),
                            score
                    );
                })
                .sorted(Comparator.comparingDouble(SearchItem::score).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private double relevanceScore(List<String> queryTerms, String text) {
        if (queryTerms.isEmpty() || text == null || text.isBlank()) {
            return 0.0;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        long matches = queryTerms.stream().filter(normalized::contains).count();
        return (double) matches / queryTerms.size();
    }

    private double freshnessScore(Instant publishedAt) {
        if (publishedAt == null) {
            return 0.0;
        }
        long ageDays = Math.max(0, Duration.between(publishedAt, Instant.now()).toDays());
        return Math.exp(-(double) ageDays / 365.0) * 0.5;
    }
}
