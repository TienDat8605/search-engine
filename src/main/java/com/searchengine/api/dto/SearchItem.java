package com.searchengine.api.dto;

import java.util.List;

public record SearchItem(
        Long questionId,
        String title,
        String source,
        List<String> tags,
        int questionScore,
        boolean answered,
        boolean accepted,
        String snippet,
        String link,
        double score
) {
}
