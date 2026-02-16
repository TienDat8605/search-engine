package com.searchengine.domain;

import java.time.Instant;
import java.util.List;

public record ProviderSearchResult(
        Long questionId,
        String url,
        String title,
        String snippet,
        SourceType source,
        int questionScore,
        boolean answered,
        Long acceptedAnswerId,
        double sourceQuality,
        Instant publishedAt,
        List<String> tags,
        String metadataJson
) {
}
