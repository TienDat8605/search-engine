package com.searchengine.integration;

import com.searchengine.config.SearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class MistralLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MistralLlmClient.class);

    private final WebClient client;
    private final SearchProperties props;

    public MistralLlmClient(
            @Qualifier("mistralWebClient") WebClient client,
            SearchProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public boolean isEnabled() {
        return props.getLlm().isEnabled() && !props.getLlm().getApiKey().isBlank();
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        if (!isEnabled()) return null;
        try {
            Map<String, Object> body = Map.of(
                    "model", props.getLlm().getModel(),
                    "max_tokens", props.getLlm().getMaxTokens(),
                    "temperature", 0.1,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            Map<?, ?> response = client.post()
                    .uri(props.getLlm().getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + props.getLlm().getApiKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(props.getLlm().getTimeoutSeconds()))
                    .block();

            if (response == null) return null;

            List<?> choices = (List<?>) response.get("choices");
            if (choices == null || choices.isEmpty()) return null;

            Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            return message == null ? null : (String) message.get("content");
        } catch (Exception e) {
            log.error("Mistral LLM call failed: {}", e.getMessage());
            return null;
        }
    }
}
