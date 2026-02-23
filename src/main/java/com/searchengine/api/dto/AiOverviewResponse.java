package com.searchengine.api.dto;

import java.util.List;

public record AiOverviewResponse(
        String answer,
        List<Citation> citations
) {
    public record Citation(
            Long questionId,
            String title,
            String url
    ) {}

    public static AiOverviewResponse empty() {
        return new AiOverviewResponse("", List.of());
    }
}
