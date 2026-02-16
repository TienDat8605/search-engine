package com.searchengine.integration;

import java.util.List;

public interface ExternalSearchClient {
    ProviderSearchPage search(String query, int limit, int offset, String sort, List<String> tags);
}
