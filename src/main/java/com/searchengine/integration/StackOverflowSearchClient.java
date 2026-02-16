package com.searchengine.integration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.searchengine.domain.ProviderSearchResult;
import com.searchengine.domain.SourceType;

@Component
public class StackOverflowSearchClient implements ExternalSearchClient {

    private final WebClient webClient;
    private final String baseUrl;
    private final String apiKey;
    private final StackExchangeBackoffManager backoffManager;

    public StackOverflowSearchClient(
            WebClient webClient,
            @Value("${search.providers.stackoverflow.base-url:https://api.stackexchange.com}") String baseUrl,
            @Value("${search.providers.stackoverflow.api-key:${STACKEXCHANGE_API_KEY:}}") String apiKey,
            StackExchangeBackoffManager backoffManager
    ) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.backoffManager = backoffManager;
    }

    @Override
    public List<ProviderSearchResult> search(String query, int limit, int offset, String sort, List<String> tags) {
        if (backoffManager.isBackoffActive()) {
            return List.of();
        }

        try {
            String normalizedSort = normalizeSort(sort);
            int safeLimit = Math.max(1, limit);
            int safeOffset = Math.max(0, offset);
            int page = (safeOffset / safeLimit) + 1;

            JsonNode response = executeSearchRequest(query, safeLimit, page, normalizedSort, tags, apiKey);

            if (isInvalidKeyResponse(response) && StringUtils.hasText(apiKey)) {
                response = executeSearchRequest(query, safeLimit, page, normalizedSort, tags, "");
            }

            backoffManager.registerFromResponse(response);

            if (response == null || !response.has("items")) {
                return List.of();
            }

            List<ProviderSearchResult> results = new ArrayList<>();
            JsonNode items = response.path("items");
            if (!items.isArray()) {
                return List.of();
            }

            for (JsonNode item : items) {
                String url = item.path("link").asText("");
                if (url.isBlank()) {
                    continue;
                }
                String title = item.path("title").asText("");
                long questionId = item.path("question_id").asLong(0L);
                int score = item.path("score").asInt(0);
                boolean answered = item.path("is_answered").asBoolean(false);
                long acceptedAnswerId = item.path("accepted_answer_id").asLong(0L);
                long creationDate = item.path("creation_date").asLong(0L);

                List<String> itemTags = new ArrayList<>();
                JsonNode tagsNode = item.path("tags");
                if (tagsNode.isArray()) {
                    for (JsonNode tag : tagsNode) {
                        itemTags.add(tag.asText());
                    }
                }

                double quality = Math.min(1.25, score / 40.0) + (answered ? 0.5 : 0.0);
                String metadata = "{\"score\":" + score + ",\"answered\":" + answered + "}";

                results.add(new ProviderSearchResult(
                    questionId > 0 ? questionId : null,
                        url,
                        title,
                        "Stack Overflow question relevant to query.",
                        SourceType.STACKOVERFLOW,
                    score,
                    answered,
                    acceptedAnswerId > 0 ? acceptedAnswerId : null,
                        quality,
                        creationDate > 0 ? Instant.ofEpochSecond(creationDate) : Instant.now(),
                    itemTags,
                        metadata
                ));
            }
            return results;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private JsonNode executeSearchRequest(
            String query,
            int limit,
            int page,
            String sort,
            List<String> tags,
            String key
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(baseUrl + "/2.3/search/advanced")
                .queryParam("order", "desc")
                .queryParam("sort", sort)
                .queryParam("site", "stackoverflow")
                .queryParam("q", query)
                .queryParam("pagesize", limit)
                .queryParam("page", page);

        if (tags != null && !tags.isEmpty()) {
            String tagged = tags.stream()
                    .map(String::trim)
                    .filter(tag -> !tag.isBlank())
                    .collect(Collectors.joining(";"));
            if (!tagged.isBlank()) {
                builder.queryParam("tagged", tagged);
            }
        }

        if (StringUtils.hasText(key)) {
            builder.queryParam("key", key);
        }

        return webClient.get()
                .uri(builder.build().encode().toUri())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    private boolean isInvalidKeyResponse(JsonNode response) {
        if (response == null) {
            return false;
        }
        String errorName = response.path("error_name").asText("");
        String errorMessage = response.path("error_message").asText("");
        return "key_invalid".equalsIgnoreCase(errorName)
                || errorMessage.toLowerCase(Locale.ROOT).contains("key");
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "relevance";
        }
        String value = sort.toLowerCase(Locale.ROOT).trim();
        return switch (value) {
            case "new" -> "creation";
            case "relevance", "votes", "activity", "creation" -> value;
            default -> "relevance";
        };
    }
}
