package com.searchengine.integration;

import java.util.List;

import com.searchengine.domain.ProviderSearchResult;

public interface ExternalSearchClient {
    List<ProviderSearchResult> search(String query, int limit, int offset, String sort, List<String> tags);
}
