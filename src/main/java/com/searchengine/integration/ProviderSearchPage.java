package com.searchengine.integration;

import java.util.List;

import com.searchengine.domain.ProviderSearchResult;

public record ProviderSearchPage(
        List<ProviderSearchResult> items,
        boolean hasMore
) {
    public static ProviderSearchPage empty() {
        return new ProviderSearchPage(List.of(), false);
    }
}
