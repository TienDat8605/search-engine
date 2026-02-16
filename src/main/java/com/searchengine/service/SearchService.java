package com.searchengine.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.searchengine.api.dto.SearchItem;
import com.searchengine.api.dto.SearchResponse;
import com.searchengine.domain.ProviderSearchResult;
import com.searchengine.integration.ExternalSearchClient;
import com.searchengine.persistence.DocumentEntity;
import com.searchengine.persistence.DocumentRepository;
import com.searchengine.persistence.QueryLogEntity;
import com.searchengine.persistence.QueryLogRepository;

@Service
public class SearchService {

    private final List<ExternalSearchClient> clients;
    private final Ranker ranker;
    private final SearchCacheService cacheService;
    private final DocumentRepository documentRepository;
    private final QueryLogRepository queryLogRepository;
    private final Executor searchExecutor;
    private final AsyncEnrichmentService asyncEnrichmentService;

    public SearchService(
            List<ExternalSearchClient> clients,
            Ranker ranker,
            SearchCacheService cacheService,
            DocumentRepository documentRepository,
            QueryLogRepository queryLogRepository,
            @Qualifier("searchExecutor") Executor searchExecutor,
            AsyncEnrichmentService asyncEnrichmentService
    ) {
        this.clients = clients;
        this.ranker = ranker;
        this.cacheService = cacheService;
        this.documentRepository = documentRepository;
        this.queryLogRepository = queryLogRepository;
        this.searchExecutor = searchExecutor;
        this.asyncEnrichmentService = asyncEnrichmentService;
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
        List<ProviderSearchResult> providerResults = runProviderSearch(query, limit, offset, sort, tags);
        List<ProviderSearchResult> deduped = deduplicate(providerResults);

        List<SearchItem> rankedItems = ranker.rank(query, deduped, limit);
        boolean hasMore = providerResults.size() >= limit;
        SearchResponse response = new SearchResponse(
                query,
                sort,
                tags,
                limit,
                offset,
                hasMore,
                Instant.now(),
                rankedItems.size(),
                rankedItems
        );
        List<ProviderSearchResult> enrichmentCandidates = selectEnrichmentCandidates(deduped, rankedItems);

        persistDocuments(deduped);
        cacheService.put(cacheKey, response);
        asyncEnrichmentService.enqueue(enrichmentCandidates);
        logQuery(query, sort, tags, limit, offset, response.total(), false);
        return response;
    }

    private List<ProviderSearchResult> runProviderSearch(String query, int limit, int offset, String sort, List<String> tags) {
        List<CompletableFuture<List<ProviderSearchResult>>> tasks = clients.stream()
                .map(client -> CompletableFuture.supplyAsync(() -> client.search(query, limit, offset, sort, tags), searchExecutor))
                .toList();

        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();

        List<ProviderSearchResult> results = new ArrayList<>();
        for (CompletableFuture<List<ProviderSearchResult>> task : tasks) {
            try {
                results.addAll(task.get());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException ignored) {
            }
        }
        return results;
    }

    private List<ProviderSearchResult> deduplicate(List<ProviderSearchResult> raw) {
        Map<String, ProviderSearchResult> byUrl = new LinkedHashMap<>();
        for (ProviderSearchResult result : raw) {
            if (result.url() == null || result.url().isBlank()) {
                continue;
            }
            byUrl.merge(
                    result.url(),
                    result,
                    (current, candidate) -> candidate.sourceQuality() > current.sourceQuality() ? candidate : current
            );
        }
        return byUrl.values().stream()
                .sorted(Comparator.comparing(ProviderSearchResult::publishedAt).reversed())
                .toList();
    }

    @Transactional
    public void persistDocuments(List<ProviderSearchResult> results) {
        List<DocumentEntity> entities = new ArrayList<>();
        for (ProviderSearchResult result : results) {
            DocumentEntity entity = new DocumentEntity();
            entity.setQuestionId(result.questionId());
            entity.setUrl(result.url());
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
        return "search:v2:" + normalizedQuery + ":" + limit + ":" + offset + ":" + sort + ":" + tagPart;
    }

    private List<ProviderSearchResult> selectEnrichmentCandidates(
            List<ProviderSearchResult> providerResults,
            List<SearchItem> rankedItems
    ) {
        Map<String, ProviderSearchResult> byUrl = new LinkedHashMap<>();
        for (ProviderSearchResult providerResult : providerResults) {
            byUrl.put(providerResult.url(), providerResult);
        }

        List<ProviderSearchResult> candidates = new ArrayList<>();
        for (SearchItem rankedItem : rankedItems) {
            ProviderSearchResult match = byUrl.get(rankedItem.link());
            if (match != null) {
                candidates.add(match);
            }
        }
        return candidates;
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "relevance";
        }
        String value = sort.toLowerCase(Locale.ROOT).trim();
        return switch (value) {
            case "new", "relevance" -> value;
            default -> "relevance";
        };
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .map(tag -> tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private void logQuery(String query, String sort, List<String> tags, int limit, int offset, int resultCount, boolean cacheHit) {
        QueryLogEntity log = new QueryLogEntity();
        log.setQueryText(query);
        log.setSort(sort);
        log.setTags(tags.isEmpty() ? "" : String.join(",", tags));
        log.setLimitValue(limit);
        log.setOffsetValue(offset);
        log.setResultCount(resultCount);
        log.setCacheHit(cacheHit);
        log.setCreatedAt(Instant.now());
        queryLogRepository.save(log);
    }
}
