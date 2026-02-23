package com.searchengine.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchengine.config.SearchProperties;
import com.searchengine.domain.ProviderSearchResult;
import com.searchengine.domain.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback search client using Jina Reader Search API (s.jina.ai).
 * Activated only when the StackExchange daily quota is exhausted.
 *
 * Strategy: search without a site filter, then keep only URLs matching
 * stackoverflow.com/questions/ since Jina can't fetch SO pages directly
 * (Cloudflare blocks them) but the search index still returns SO titles/URLs.
 */
@Component
public class JinaSearchClient implements ExternalSearchClient {

    private static final Logger log = LoggerFactory.getLogger(JinaSearchClient.class);

    private static final Pattern SO_QUESTION_ID = Pattern.compile("stackoverflow\\.com/questions/(\\d+)");

    private final WebClient client;
    private final SearchProperties props;
    private final ObjectMapper objectMapper;

    public JinaSearchClient(
            @Qualifier("webClient") WebClient client,
            SearchProperties props,
            ObjectMapper objectMapper) {
        this.client = client;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return !props.getJina().getSearchApiKey().isBlank();
    }

    @Override
    public ProviderSearchPage search(String query, int limit, int offset, String sort, List<String> tags) {
        if (!isEnabled()) {
            return ProviderSearchPage.empty();
        }
        // Append "stackoverflow" so Jina's web search naturally surfaces SO results
        String jinaQuery = query + " site:stackoverflow.com";
        String encodedQuery = UriUtils.encodePathSegment(jinaQuery, StandardCharsets.UTF_8);
        String url = props.getJina().getSearchUrl() + "/" + encodedQuery;

        try {
            String json = client.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + props.getJina().getSearchApiKey())
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(props.getJina().getTimeoutMillis()))
                    .block();

            if (json == null) return ProviderSearchPage.empty();

            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (!data.isArray()) return ProviderSearchPage.empty();

            List<ProviderSearchResult> results = new ArrayList<>();
            for (JsonNode item : data) {
                String itemUrl = item.path("url").asText("");
                Matcher m = SO_QUESTION_ID.matcher(itemUrl);
                if (!m.find()) continue;  // skip non-SO results

                long questionId = Long.parseLong(m.group(1));
                String title = item.path("title").asText("").trim();
                String description = item.path("description").asText("").trim();

                results.add(new ProviderSearchResult(
                        questionId,
                        itemUrl,
                        title.isEmpty() ? "Stack Overflow question " + questionId : title,
                        description,
                        SourceType.STACKOVERFLOW,
                        0,       // no score info from Jina
                        true,    // assume answered (unknown)
                        null,    // no accepted answer id
                        0.5,     // degraded-mode quality signal
                        Instant.now(),
                        tags != null ? tags : List.of(),
                        null
                ));

                if (results.size() >= limit) break;
            }

            log.info("Jina search fallback returned {} SO results for: {}", results.size(), query);
            return new ProviderSearchPage(results, false);
        } catch (Exception e) {
            log.error("Jina search failed: {}", e.getMessage());
            return ProviderSearchPage.empty();
        }
    }
}
