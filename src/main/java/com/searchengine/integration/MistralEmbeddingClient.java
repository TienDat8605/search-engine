package com.searchengine.integration;

import com.searchengine.config.SearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MistralEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(MistralEmbeddingClient.class);

    private final WebClient client;
    private final SearchProperties props;

    public MistralEmbeddingClient(
            @Qualifier("mistralWebClient") WebClient client,
            SearchProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public boolean isEnabled() {
        return props.getEmbedding().isEnabled() && !props.getEmbedding().getApiKey().isBlank();
    }

    @Override
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (!isEnabled() || texts.isEmpty()) return Collections.emptyList();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", props.getEmbedding().getModel());
            body.put("input", texts);

            Map<?, ?> response = client.post()
                    .uri("https://api.mistral.ai/v1/embeddings")
                    .header("Authorization", "Bearer " + props.getEmbedding().getApiKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(props.getEmbedding().getTimeoutMillis()))
                    .block();

            if (response == null) return Collections.emptyList();

            List<?> data = (List<?>) response.get("data");
            List<float[]> result = new ArrayList<>(data.size());
            for (Object item : data) {
                List<?> vec = (List<?>) ((Map<?, ?>) item).get("embedding");
                float[] floats = new float[vec.size()];
                for (int i = 0; i < vec.size(); i++) floats[i] = ((Number) vec.get(i)).floatValue();
                result.add(floats);
            }
            return result;
        } catch (Exception e) {
            log.error("Mistral embedding failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
