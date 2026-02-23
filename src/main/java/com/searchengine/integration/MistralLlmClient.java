package com.searchengine.integration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchengine.config.SearchProperties;

@Component
public class MistralLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MistralLlmClient.class);
    private static final String MISTRAL_CHAT_URL = "https://api.mistral.ai/v1/chat/completions";

    private final WebClient webClient;
    private final SearchProperties searchProperties;
    private final ObjectMapper objectMapper;

    public MistralLlmClient(
            @Qualifier("llmWebClient") WebClient webClient,
            SearchProperties searchProperties,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.searchProperties = searchProperties;
        this.objectMapper = objectMapper;
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
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", cfg.getModel(),
                    "max_tokens", cfg.getMaxTokens(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            ));

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
}
