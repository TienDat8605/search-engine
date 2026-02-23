package com.searchengine.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.searchengine.api.dto.SearchItem;
import com.searchengine.api.dto.SearchResponse;
import com.searchengine.domain.ProviderSearchResult;
import com.searchengine.integration.JinaSearchClient;
import com.searchengine.integration.ProviderSearchPage;
import com.searchengine.integration.StackExchangeBackoffManager;
import com.searchengine.integration.StackOverflowSearchClient;
import com.searchengine.persistence.DocumentEntity;
import com.searchengine.persistence.DocumentRepository;
import com.searchengine.persistence.QueryLogEntity;
import com.searchengine.persistence.QueryLogRepository;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final int MAX_PROVIDER_FETCH_SIZE = 50;

    private final StackOverflowSearchClient soClient;
    private final JinaSearchClient jinaClient;
    private final StackExchangeBackoffManager backoffManager;
    private final Ranker ranker;
    private final SearchCacheService cacheService;
    private final DocumentRepository documentRepository;
    private final QueryLogRepository queryLogRepository;
    private final java.util.concurrent.Executor searchExecutor;
    private final AsyncEnrichmentService asyncEnrichmentService;
    private final VectorSearchService vectorSearchService;
    private final ClickBoostService clickBoostService;

    public SearchService(
            StackOverflowSearchClient soClient,
            JinaSearchClient jinaClient,
            StackExchangeBackoffManager backoffManager,
            Ranker ranker,
            SearchCacheService cacheService,
            DocumentRepository documentRepository,
            QueryLogRepository queryLogRepository,
            @Qualifier("searchExecutor") java.util.concurrent.Executor searchExecutor,
            AsyncEnrichmentService asyncEnrichmentService,
            VectorSearchService vectorSearchService,
            ClickBoostService clickBoostService) {
        this.soClient = soClient;
        this.jinaClient = jinaClient;
        this.backoffManager = backoffManager;
        this.ranker = ranker;
        this.cacheService = cacheService;
        this.documentRepository = documentRepository;
        this.queryLogRepository = queryLogRepository;
        this.searchExecutor = searchExecutor;
        this.asyncEnrichmentService = asyncEnrichmentService;
        this.vectorSearchService = vectorSearchService;
        this.clickBoostService = clickBoostService;
    }

    public SearchResponse search(String query, int limit, int offset, String sort, List<String> tags) {
        String normalizedQuery = query == null ? "" : query.trim();
        int normalizedOffset = Math.max(0, offset);
        String normalizedSort = normalizeSort(sort);
        List<String> normalizedTags = normalizeTags(tags);
        String cacheKey = buildCacheKey(normalizedQuery, limit, normalizedOffset, normalizedSort, normalizedTags);

        SearchResponse cached = cacheService.get(cacheKey).orElse(null);
        if (cached != null) {
            logQuery(normalizedQuery, normalizedSort, normalizedTags, limit, normalizedOffset, cached.total(), true);
            return cached;
        }
        return executeAndCache(normalizedQuery, limit, normalizedOffset, normalizedSort, normalizedTags, cacheKey);
    }

    private SearchResponse executeAndCache(String query, int limit, int offset, String sort, List<String> tags, String cacheKey) {
        int fetchSize = computeProviderFetchSize(limit);
        ProviderSearchPage providerPage = runProviderSearch(query, fetchSize, offset, sort, tags);
        List<ProviderSearchResult> deduped = deduplicate(providerPage.items());

        List<String> candidateUrls = deduped.stream().map(ProviderSearchResult::url).toList();
        Map<String, Double> clickBoosts = clickBoostService.getBoosts(query, candidateUrls);

        List<SearchItem> rankedItems = ranker.rank(query, deduped, limit, clickBoosts);
        boolean semanticMode = false;

        if (vectorSearchService != null && !rankedItems.isEmpty()) {
            try {
                List<SearchItem> reranked = vectorSearchService.rerank(query, rankedItems);
                if (reranked != rankedItems) {
                    rankedItems = reranked;
                    semanticMode = true;
                }
            } catch (Exception ignored) {
            }
        }

        SearchResponse response = new SearchResponse(
                query, sort, tags, limit, offset,
                providerPage.hasMore(), providerPage.hasMore(),
                Instant.now(), rankedItems.size(), rankedItems, semanticMode);

        // Side-effects must never prevent the search response from being returned
        try {
            persistDocuments(deduped);
        } catch (Exception e) {
            log.error("Failed to persist documents for query '{}': {}", query, e.getMessage(), e);
        }
        try {
            cacheService.put(cacheKey, response);
        } catch (Exception e) {
            log.warn("Failed to cache search response for query '{}': {}", query, e.getMessage());
        }
        try {
            List<ProviderSearchResult> enrichmentCandidates = selectEnrichmentCandidates(deduped, rankedItems);
            asyncEnrichmentService.enqueue(enrichmentCandidates);
        } catch (Exception e) {
            log.warn("Failed to enqueue enrichment for query '{}': {}", query, e.getMessage());
        }
        try {
            logQuery(query, sort, tags, limit, offset, response.total(), false);
        } catch (Exception e) {
            log.warn("Failed to log query '{}': {}", query, e.getMessage());
        }
        return response;
    }

    /**
     * Primary: StackOverflow API. Fallback: Jina when SO quota exhausted or backoff active.
     */
    private ProviderSearchPage runProviderSearch(String query, int limit, int offset, String sort, List<String> tags) {
        boolean soUnavailable = backoffManager.isBackoffActive() || backoffManager.isQuotaExhausted();

        if (!soUnavailable) {
            try {
                ProviderSearchPage page = CompletableFuture
                        .supplyAsync(() -> soClient.search(query, limit, offset, sort, tags), searchExecutor)
                        .join();
                if (!page.items().isEmpty()) {
                    return page;
                }
                soUnavailable = backoffManager.isQuotaExhausted() || backoffManager.isBackoffActive();
            } catch (Exception e) {
                log.error("StackOverflow search failed for query '{}': {}", query, e.getMessage(), e);
                soUnavailable = true;
            }
        }

        if (soUnavailable && jinaClient.isEnabled()) {
            log.warn("SO unavailable (backoff={}, quotaExhausted={}); using Jina fallback",
                    backoffManager.isBackoffActive(), backoffManager.isQuotaExhausted());
            try {
                return CompletableFuture
                        .supplyAsync(() -> jinaClient.search(query, limit, offset, sort, tags), searchExecutor)
                        .join();
            } catch (Exception e) {
                log.error("Jina fallback search failed for query '{}': {}", query, e.getMessage(), e);
            }
        }

        return ProviderSearchPage.empty();
    }

    private List<ProviderSearchResult> deduplicate(List<ProviderSearchResult> raw) {
        Map<String, ProviderSearchResult> byUrl = new LinkedHashMap<>();
        for (ProviderSearchResult r : raw) {
            if (r.url() == null || r.url().isBlank()) continue;
            byUrl.merge(r.url(), r,
                    (cur, cand) -> cand.sourceQuality() > cur.sourceQuality() ? cand : cur);
        }
        return new ArrayList<>(byUrl.values());
    }

    @Transactional
    public void persistDocuments(List<ProviderSearchResult> results) {
        List<DocumentEntity> entities = new ArrayList<>();
        for (ProviderSearchResult result : results) {
            DocumentEntity entity = null;
            if (result.questionId() != null) {
                entity = documentRepository.findByQuestionId(result.questionId()).orElse(null);
            }
            if (entity == null) {
                entity = documentRepository.findById(result.url()).orElseGet(DocumentEntity::new);
            }
            entity.setQuestionId(result.questionId());
            if (entity.getUrl() == null || entity.getUrl().isBlank()) entity.setUrl(result.url());
            entity.setSource(result.source().name());
            entity.setTitle(result.title());
            entity.setNormalizedText(normalize(result.title() + " " + result.snippet()));
            entity.setMetadataJson(result.metadataJson());
            entity.setTags(String.join(",", result.tags()));
            entity.setQuestionText("");
            entity.setBestAnswerText("");
            entity.setFetchedAt(Instant.now());
            entities.add(entity);
        }
        documentRepository.saveAll(entities);
    }

    private String buildCacheKey(String query, int limit, int offset, String sort, List<String> tags) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        String tagPart = tags.isEmpty() ? "*" : String.join(",", tags);
        return "search:v5:" + normalizedQuery + ":" + limit + ":" + offset + ":" + sort + ":" + tagPart;
    }

    private List<ProviderSearchResult> selectEnrichmentCandidates(
            List<ProviderSearchResult> providerResults, List<SearchItem> rankedItems) {
        Map<String, ProviderSearchResult> byUrl = new LinkedHashMap<>();
        for (ProviderSearchResult r : providerResults) byUrl.put(r.url(), r);
        List<ProviderSearchResult> candidates = new ArrayList<>();
        for (SearchItem item : rankedItems) {
            ProviderSearchResult match = byUrl.get(item.link());
            if (match != null) candidates.add(match);
        }
        return candidates;
    }

    private int computeProviderFetchSize(int limit) {
        return Math.min(MAX_PROVIDER_FETCH_SIZE, limit + 5);
    }

    private String normalize(String input) {
        if (input == null) return "";
        return input.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) return "relevance";
        return switch (sort.toLowerCase(Locale.ROOT).trim()) {
            case "new", "relevance" -> sort.toLowerCase(Locale.ROOT).trim();
            default -> "relevance";
        };
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return List.of();
        return tags.stream()
                .map(tag -> tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private void logQuery(String query, String sort, List<String> tags, int limit, int offset, int resultCount, boolean cacheHit) {
        QueryLogEntity logEntry = new QueryLogEntity();
        logEntry.setQueryText(query);
        logEntry.setSort(sort);
        logEntry.setTags(tags.isEmpty() ? "" : String.join(",", tags));
        logEntry.setLimitValue(limit);
        logEntry.setOffsetValue(offset);
        logEntry.setResultCount(resultCount);
        logEntry.setCacheHit(cacheHit);
        logEntry.setCreatedAt(Instant.now());
        queryLogRepository.save(logEntry);
    }
}
