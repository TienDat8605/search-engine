package com.searchengine.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.searchengine.api.dto.SearchItem;
import com.searchengine.domain.ProviderSearchResult;

@Component
public class Ranker {

    public List<SearchItem> rank(String query, List<ProviderSearchResult> results, int limit) {
        return rank(query, results, limit, Map.of());
    }

    public List<SearchItem> rank(String query, List<ProviderSearchResult> results, int limit, Map<String, Double> clickBoosts) {
        int n = results.size();
        List<SearchItem> items = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            ProviderSearchResult result = results.get(i);

            double positionScore = (double) (n - i) / n;
            double voteScore = Math.min(1.0, Math.log1p(Math.max(0, result.questionScore())) / Math.log1p(100));
            double answerScore = (result.acceptedAnswerId() != null) ? 1.0 : result.answered() ? 0.6 : 0.2;
            double clickBoost = clickBoosts.getOrDefault(result.url(), 0.0);

            double score = 0.70 * positionScore + 0.10 * voteScore + 0.10 * answerScore + 0.10 * clickBoost;

            items.add(new SearchItem(
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
            ));
        }

        return items.stream()
                .sorted(Comparator.comparingDouble(SearchItem::score).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}
