package com.searchengine.api.dto;

import java.time.Instant;
import java.util.List;

public record DocumentResponse(
        Long questionId,
        String title,
        String url,
        String source,
        List<String> tags,
        String questionText,
        String bestAnswerText,
        String metadataJson,
        Instant fetchedAt
) {
}
