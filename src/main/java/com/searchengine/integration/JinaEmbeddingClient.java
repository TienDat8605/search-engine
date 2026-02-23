package com.searchengine.integration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.searchengine.config.SearchProperties;

// @Component — de-registered; Mistral is now the active EmbeddingClient (@see MistralEmbeddingClient)
// Jina API key (JINA_API_KEY) is now used exclusively for SO search fallback via JinaSearchClient.
public class JinaEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(JinaEmbeddingClient.class);
    private static final String JINA_API_URL = "https://api.jina.ai/v1/embeddings";

    private final WebClient webClient;
    private final SearchProperties searchProperties;

    public JinaEmbeddingClient(
            @Qualifier("embeddingWebClient") WebClient webClient,
            SearchProperties searchProperties
    ) {
        this.webClient = webClient;
        this.searchProperties = searchProperties;
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
            String requestBody = buildRequestBody(cfg.getModel(), texts);

            JsonNode response = webClient.post()
                    .uri(JINA_API_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout);

            if (response == null || !response.path("data").isArray()) {
                log.warn("Jina embedding API returned unexpected response");
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
            log.warn("Jina embedding request failed: {}", e.getMessage());
            return nullList(texts.size());
        }
    }

    private String buildRequestBody(String model, List<String> texts) {
        StringBuilder sb = new StringBuilder("{\"model\":\"");
        sb.append(escapeJson(model));
        sb.append("\",\"input\":[");
        for (int i = 0; i < texts.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(texts.get(i))).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private List<float[]> nullList(int size) {
        List<float[]> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(null);
        }
        return list;
    }
}
