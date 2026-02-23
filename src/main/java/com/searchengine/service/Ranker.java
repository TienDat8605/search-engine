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
            // Base score preserves SO's ordering: position 0 is best, so score descends.
            // Click boost (0–0.5) can nudge a result up by at most a few positions.
            double positionScore = (double) (n - i) / n;
            double clickBoost = clickBoosts.getOrDefault(result.url(), 0.0);
            double score = positionScore + clickBoost;

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
