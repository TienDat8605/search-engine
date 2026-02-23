package com.searchengine.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchengine.api.dto.AiOverviewResponse;
import com.searchengine.api.dto.SearchItem;
import com.searchengine.api.dto.SearchResponse;
import com.searchengine.config.SearchProperties;
import com.searchengine.integration.LlmClient;

@Service
public class AiOverviewService {

    private static final Logger log = LoggerFactory.getLogger(AiOverviewService.class);
    private static final String CACHE_KEY_PREFIX = "ai:v1:";
    private static final int CONTEXT_DOC_LIMIT = 5;

    private static final String SYSTEM_PROMPT =
            "You are a helpful coding assistant that provides concise, accurate summaries of Stack Overflow answers. "
            + "Given the following search results, provide a clear and helpful overview that directly answers the user's question. "
            + "Reference specific answers using [SO-1], [SO-2] notation when citing a source. "
            + "Keep the overview to 3-5 sentences. Focus on practical, actionable information.";

    private final LlmClient llmClient;
    private final SearchService searchService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public AiOverviewService(
            LlmClient llmClient,
            SearchService searchService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            SearchProperties searchProperties
    ) {
        this.llmClient = llmClient;
        this.searchService = searchService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = Duration.ofMinutes(searchProperties.getLlm().getCacheTtlMinutes());
    }

    public boolean isEnabled() {
        return llmClient.isEnabled();
    }

    public AiOverviewResponse generateOverview(String query) {
        String cacheKey = buildCacheKey(query);

        AiOverviewResponse cached = tryGetFromCache(cacheKey);
        if (cached != null) {
            return new AiOverviewResponse(cached.overview(), cached.citations(), true);
        }

        SearchResponse searchResults = searchService.search(query, CONTEXT_DOC_LIMIT, 0, "relevance", List.of());
        List<SearchItem> items = searchResults.items();

        if (items.isEmpty()) {
            return new AiOverviewResponse("No relevant results found to generate an overview.", List.of(), false);
        }

        String userPrompt = buildUserPrompt(query, items);
        String rawOverview = llmClient.complete(SYSTEM_PROMPT, userPrompt);

        if (rawOverview == null || rawOverview.isBlank()) {
            log.warn("LLM returned empty response for query: {}", query);
            return new AiOverviewResponse("Unable to generate an overview at this time.", List.of(), false);
        }

        List<AiOverviewResponse.Citation> citations = buildCitations(items);
        AiOverviewResponse response = new AiOverviewResponse(rawOverview.trim(), citations, false);

        tryPutInCache(cacheKey, response);
        return response;
    }

    private String buildUserPrompt(String query, List<SearchItem> items) {
        StringBuilder sb = new StringBuilder("Question: ").append(query).append("\n\nSources:\n");
        for (int i = 0; i < items.size(); i++) {
            SearchItem item = items.get(i);
            sb.append("[SO-").append(i + 1).append("] ")
              .append(item.title())
              .append(": ")
              .append(item.snippet() != null ? item.snippet() : "No snippet available.")
              .append("\n");
        }
        sb.append("\nPlease provide a concise overview that answers the question above, citing sources as [SO-n].");
        return sb.toString();
    }

    private List<AiOverviewResponse.Citation> buildCitations(List<SearchItem> items) {
        List<AiOverviewResponse.Citation> citations = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            SearchItem item = items.get(i);
            citations.add(new AiOverviewResponse.Citation(i + 1, item.title(), item.link()));
        }
        return citations;
    }

    private String buildCacheKey(String query) {
        return CACHE_KEY_PREFIX + query.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private AiOverviewResponse tryGetFromCache(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, AiOverviewResponse.class);
        } catch (Exception e) {
            log.warn("Failed to read AI overview from cache for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void tryPutInCache(String key, AiOverviewResponse response) {
        try {
            String payload = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, payload, cacheTtl);
        } catch (Exception e) {
            log.warn("Failed to cache AI overview for key {}: {}", key, e.getMessage());
        }
    }
}
