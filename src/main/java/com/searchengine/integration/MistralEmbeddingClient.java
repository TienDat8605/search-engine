package com.searchengine.integration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchengine.config.SearchProperties;

@Component
@ConditionalOnProperty(prefix = "search.embedding", name = "provider", havingValue = "mistral", matchIfMissing = true)
public class MistralEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(MistralEmbeddingClient.class);
    private static final String MISTRAL_EMBED_URL = "https://api.mistral.ai/v1/embeddings";

    private final WebClient webClient;
    private final SearchProperties searchProperties;
    private final ObjectMapper objectMapper;

    public MistralEmbeddingClient(
            @Qualifier("embeddingWebClient") WebClient webClient,
            SearchProperties searchProperties,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.searchProperties = searchProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isEnabled() {
        SearchProperties.Embedding cfg = searchProperties.getEmbedding();
        return cfg.isEnabled() && !cfg.getApiKey().isBlank();
    }

    @Override
    public float[] embed(String text) {
        if (!isEnabled() || text == null || text.isBlank()) {
            return null;
        }
        List<float[]> results = embedBatch(List.of(text));
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (!isEnabled() || texts == null || texts.isEmpty()) {
            return List.of();
        }

        SearchProperties.Embedding cfg = searchProperties.getEmbedding();
        Duration timeout = Duration.ofMillis(cfg.getTimeoutMillis());

        try {
            String requestBody = objectMapper.writeValueAsString(
                    Map.of("model", cfg.getModel(), "input", texts));

            JsonNode response = webClient.post()
                    .uri(MISTRAL_EMBED_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout);

            if (response == null || !response.path("data").isArray()) {
                log.warn("Mistral embedding API returned unexpected response");
                return nullList(texts.size());
            }

            float[][] ordered = new float[texts.size()][];
            for (JsonNode item : response.path("data")) {
                int index = item.path("index").asInt(-1);
                if (index >= 0 && index < texts.size() && item.path("embedding").isArray()) {
                    JsonNode embNode = item.path("embedding");
                    float[] vec = new float[embNode.size()];
                    for (int i = 0; i < embNode.size(); i++) {
                        vec[i] = (float) embNode.get(i).asDouble();
                    }
                    ordered[index] = vec;
                }
            }

            List<float[]> result = new ArrayList<>(texts.size());
            for (float[] v : ordered) {
                result.add(v);
            }
            return result;

        } catch (Exception e) {
            log.warn("Mistral embedding request failed: {}", e.getMessage());
            return nullList(texts.size());
        }
    }

    private List<float[]> nullList(int size) {
        List<float[]> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(null);
        }
        return list;
    }
}
