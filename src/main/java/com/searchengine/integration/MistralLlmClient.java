package com.searchengine.integration;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.searchengine.config.SearchProperties;

@Component
public class MistralLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MistralLlmClient.class);
    private static final String MISTRAL_CHAT_URL = "https://api.mistral.ai/v1/chat/completions";

    private final WebClient webClient;
    private final SearchProperties searchProperties;

    public MistralLlmClient(
            @Qualifier("llmWebClient") WebClient webClient,
            SearchProperties searchProperties
    ) {
        this.webClient = webClient;
        this.searchProperties = searchProperties;
    }

    @Override
    public boolean isEnabled() {
        SearchProperties.Llm cfg = searchProperties.getLlm();
        return cfg.isEnabled() && !cfg.getApiKey().isBlank();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!isEnabled()) {
            return null;
        }

        SearchProperties.Llm cfg = searchProperties.getLlm();
        Duration timeout = Duration.ofMillis(cfg.getTimeoutMillis());

        try {
            String requestBody = buildRequestBody(cfg.getModel(), cfg.getMaxTokens(), systemPrompt, userPrompt);

            JsonNode response = webClient.post()
                    .uri(MISTRAL_CHAT_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout);

            if (response == null || !response.path("choices").isArray() || response.path("choices").isEmpty()) {
                log.warn("Mistral LLM API returned unexpected response");
                return null;
            }

            return response.path("choices").get(0).path("message").path("content").asText(null);

        } catch (Exception e) {
            log.warn("Mistral LLM request failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildRequestBody(String model, int maxTokens, String systemPrompt, String userPrompt) {
        return "{\"model\":\"" + escapeJson(model) + "\""
                + ",\"max_tokens\":" + maxTokens
                + ",\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"}"
                + ",{\"role\":\"user\",\"content\":\"" + escapeJson(userPrompt) + "\"}"
                + "]}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
