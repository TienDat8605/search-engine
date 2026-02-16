package com.searchengine.api.dto;

import java.time.Instant;
import java.util.List;

public record SearchResponse(
        String query,
        String sort,
        List<String> tags,
        int limit,
        int offset,
        boolean hasMore,
        boolean providerHasMore,
        Instant generatedAt,
        int total,
        List<SearchItem> items
) {
}
