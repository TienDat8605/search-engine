package com.searchengine.api.dto;

import java.util.List;

public record AiOverviewResponse(
        String overview,
        List<Citation> citations,
        boolean cached
) {
    public record Citation(int index, String title, String url) {}
}
