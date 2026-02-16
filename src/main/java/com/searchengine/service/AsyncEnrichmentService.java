package com.searchengine.service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.searchengine.config.SearchProperties;
import com.searchengine.domain.ProviderSearchResult;
import com.searchengine.domain.SourceType;
import com.searchengine.integration.StackExchangeBackoffManager;
import com.searchengine.persistence.DocumentEntity;
import com.searchengine.persistence.DocumentRepository;

@Service
public class AsyncEnrichmentService {

    private static final Pattern STACKOVERFLOW_ID_PATTERN = Pattern.compile("/questions/(\\d+)");

    private final WebClient webClient;
    private final DocumentRepository documentRepository;
    private final SearchProperties searchProperties;
    private final Executor enrichmentExecutor;
    private final StackExchangeBackoffManager backoffManager;
    private final Semaphore globalSemaphore;
    private final Map<String, Semaphore> perHostSemaphores = new ConcurrentHashMap<>();

    public AsyncEnrichmentService(
            WebClient webClient,
            DocumentRepository documentRepository,
            SearchProperties searchProperties,
            @Qualifier("enrichmentExecutor") Executor enrichmentExecutor,
            StackExchangeBackoffManager backoffManager
    ) {
        this.webClient = webClient;
        this.documentRepository = documentRepository;
        this.searchProperties = searchProperties;
        this.enrichmentExecutor = enrichmentExecutor;
        this.backoffManager = backoffManager;
        this.globalSemaphore = new Semaphore(Math.max(1, searchProperties.getEnrichment().getMaxConcurrentFetches()));
    }

    public void enqueue(List<ProviderSearchResult> rankedCandidates) {
        SearchProperties.Enrichment enrichment = searchProperties.getEnrichment();
        if (!enrichment.isEnabled() || rankedCandidates == null || rankedCandidates.isEmpty()) {
            return;
        }

        int topN = Math.min(Math.max(1, enrichment.getTopN()), rankedCandidates.size());
        List<ProviderSearchResult> candidates = rankedCandidates.subList(0, topN);

        for (ProviderSearchResult candidate : candidates) {
            CompletableFuture.runAsync(() -> enrichAndPersist(candidate), enrichmentExecutor);
        }
    }

    private void enrichAndPersist(ProviderSearchResult result) {
        URI uri;
        try {
            uri = URI.create(result.url());
        } catch (Exception ignored) {
            return;
        }

        String host = uri.getHost() == null ? "unknown" : uri.getHost();
        Semaphore hostSemaphore = perHostSemaphores.computeIfAbsent(
                host,
                ignored -> new Semaphore(Math.max(1, searchProperties.getEnrichment().getPerHostLimit()))
        );

        if (!globalSemaphore.tryAcquire()) {
            return;
        }
        if (!hostSemaphore.tryAcquire()) {
            globalSemaphore.release();
            return;
        }

        try {
            EnrichedContent enriched = fetchEnrichedSnippet(result);
            if (enriched == null || enriched.snippet().isBlank()) {
                return;
            }

            DocumentEntity entity = documentRepository.findById(result.url()).orElseGet(DocumentEntity::new);
            entity.setQuestionId(result.questionId());
            entity.setUrl(result.url());
            entity.setSource(result.source().name());
            entity.setTitle(result.title());
            entity.setQuestionText(enriched.questionText());
            entity.setBestAnswerText(enriched.bestAnswerText());
            entity.setNormalizedText(normalize((result.title() == null ? "" : result.title()) + " " + enriched.snippet()));
            entity.setMetadataJson(mergeMetadata(result.metadataJson(), "{\"enriched\":true,\"enriched_at\":\"" + Instant.now() + "\"}"));
            entity.setTags(String.join(",", result.tags()));
            entity.setFetchedAt(Instant.now());
            documentRepository.save(entity);
        } finally {
            hostSemaphore.release();
            globalSemaphore.release();
        }
    }

    private EnrichedContent fetchEnrichedSnippet(ProviderSearchResult result) {
        if (result.source() != SourceType.STACKOVERFLOW) {
            return new EnrichedContent("", "", result.snippet());
        }
        return enrichStackOverflow(result.url());
    }

    private EnrichedContent enrichStackOverflow(String url) {
        if (backoffManager.isBackoffActive()) {
            return EnrichedContent.empty();
        }

        Matcher matcher = STACKOVERFLOW_ID_PATTERN.matcher(url);
        if (!matcher.find()) {
            return EnrichedContent.empty();
        }
        String questionId = matcher.group(1);
        Duration timeout = Duration.ofMillis(searchProperties.getEnrichment().getFetchTimeoutMillis());
        String apiKey = searchProperties.getProviders().getStackoverflow().getApiKey();

        try {
            UriComponentsBuilder questionUriBuilder = UriComponentsBuilder
                    .fromUriString(searchProperties.getProviders().getStackoverflow().getBaseUrl() + "/2.3/questions/{id}")
                .queryParam("order", "desc")
                .queryParam("sort", "activity")
                .queryParam("site", "stackoverflow")
                .queryParam("filter", "withbody");

            if (StringUtils.hasText(apiKey)) {
            questionUriBuilder.queryParam("key", apiKey);
            }

            JsonNode questionResponse = webClient.get()
                .uri(questionUriBuilder.buildAndExpand(questionId).encode().toUri())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout);

            backoffManager.registerFromResponse(questionResponse);

            if (questionResponse == null || !questionResponse.path("items").isArray() || questionResponse.path("items").isEmpty()) {
                return EnrichedContent.empty();
            }

            JsonNode question = questionResponse.path("items").get(0);
            String questionBody = stripHtml(question.path("body").asText(""));
            long acceptedAnswerId = question.path("accepted_answer_id").asLong(0L);
            String answerBody = "";

            if (acceptedAnswerId > 0) {
                UriComponentsBuilder answerUriBuilder = UriComponentsBuilder
                    .fromUriString(searchProperties.getProviders().getStackoverflow().getBaseUrl() + "/2.3/answers/{id}")
                    .queryParam("order", "desc")
                    .queryParam("sort", "activity")
                    .queryParam("site", "stackoverflow")
                    .queryParam("filter", "withbody");

                if (StringUtils.hasText(apiKey)) {
                    answerUriBuilder.queryParam("key", apiKey);
                }

                JsonNode answerResponse = webClient.get()
                    .uri(answerUriBuilder.buildAndExpand(acceptedAnswerId).encode().toUri())
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block(timeout);

                backoffManager.registerFromResponse(answerResponse);

                if (answerResponse != null && answerResponse.path("items").isArray() && !answerResponse.path("items").isEmpty()) {
                    answerBody = stripHtml(answerResponse.path("items").get(0).path("body").asText(""));
                }
            }

            String combined = (questionBody.isBlank() ? "" : "Q: " + questionBody) +
                    (answerBody.isBlank() ? "" : " A: " + answerBody);
            return new EnrichedContent(questionBody, answerBody, truncate(combined, 900));
        } catch (RuntimeException ignored) {
            return EnrichedContent.empty();
        }
    }

    private record EnrichedContent(String questionText, String bestAnswerText, String snippet) {
        private static EnrichedContent empty() {
            return new EnrichedContent("", "", "");
        }
    }

    private String stripHtml(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return input
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String truncate(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) {
            return input == null ? "" : input;
        }
        return input.substring(0, maxLength).trim() + "...";
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private String mergeMetadata(String base, String extra) {
        if (base == null || base.isBlank()) {
            return extra;
        }
        if (extra == null || extra.isBlank()) {
            return base;
        }
        return "{\"base\":" + quote(base) + ",\"phase2\":" + quote(extra) + "}";
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
