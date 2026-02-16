package com.searchengine.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.searchengine.api.dto.SearchItem;
import com.searchengine.domain.ProviderSearchResult;

@Component
public class Ranker {

    public List<SearchItem> rank(String query, List<ProviderSearchResult> results, int limit) {
    String normalizedQuery = normalize(query);
    List<String> terms = Arrays.stream(normalizedQuery.split("\\s+"))
                .filter(term -> !term.isBlank())
                .toList();
    Set<String> queryTerms = new HashSet<>(terms);

        return results.stream()
                .map(result -> {
            double relevance = relevanceScore(normalizedQuery, queryTerms, result.title(), result.snippet());
            double quality = qualityScore(result);
                    double freshness = freshnessScore(result.publishedAt());
            double score = relevance + quality + freshness;

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

    private double relevanceScore(String normalizedQuery, Set<String> queryTerms, String title, String snippet) {
        if (queryTerms.isEmpty()) {
            return 0.0;
        }

        String normalizedTitle = normalize(title);
        String normalizedSnippet = normalize(snippet);
        String normalizedText = (normalizedTitle + " " + normalizedSnippet).trim();

        if (normalizedText.isBlank()) {
            return 0.0;
        }

        long textMatches = queryTerms.stream().filter(normalizedText::contains).count();
        long titleMatches = queryTerms.stream().filter(normalizedTitle::contains).count();

        double termCoverage = (double) textMatches / queryTerms.size();
        double titleCoverage = (double) titleMatches / queryTerms.size();
        double phraseBoost = !normalizedQuery.isBlank() && normalizedText.contains(normalizedQuery) ? 0.8 : 0.0;

        return (termCoverage * 1.2) + (titleCoverage * 0.8) + phraseBoost;
    }

    private double qualityScore(ProviderSearchResult result) {
        double sourceQuality = Math.max(0.0, result.sourceQuality());
        double acceptedBonus = result.acceptedAnswerId() != null ? 0.35 : 0.0;
        double answeredBonus = result.answered() ? 0.2 : 0.0;
        double voteSignal = Math.log1p(Math.max(0, result.questionScore())) / Math.log1p(100) * 0.8;

        double tagSignal = 0.0;
        if (result.tags() != null && !result.tags().isEmpty()) {
            tagSignal = Math.min(0.45, result.tags().size() * 0.05);
        }

        return sourceQuality + acceptedBonus + answeredBonus + voteSignal + tagSignal;
    }

    private double freshnessScore(Instant publishedAt) {
        if (publishedAt == null) {
            return 0.0;
        }
        long ageDays = Math.max(0, Duration.between(publishedAt, Instant.now()).toDays());
        return Math.exp(-(double) ageDays / 540.0) * 0.45;
    }

    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
