package com.searchengine.api;

import com.searchengine.domain.ProviderSearchResult;
import com.searchengine.domain.SourceType;
import com.searchengine.integration.ProviderSearchPage;
import com.searchengine.integration.StackOverflowSearchClient;
import com.searchengine.service.AsyncEnrichmentService;
import com.searchengine.service.SearchCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StackOverflowSearchClient stackOverflowSearchClient;

    @MockBean
    private SearchCacheService searchCacheService;

    @MockBean
    private AsyncEnrichmentService asyncEnrichmentService;

    @BeforeEach
    void setup() {
        when(searchCacheService.get(anyString())).thenReturn(Optional.empty());
        doNothing().when(searchCacheService).put(anyString(), any());
        doNothing().when(asyncEnrichmentService).enqueue(anyList());
    }

    @Test
    void searchEndpoint_returnsPhase1Contract() throws Exception {
        ProviderSearchResult sample = new ProviderSearchResult(
                12345L,
                "https://stackoverflow.com/questions/12345/example",
                                "How to fix dependency injection in Spring Boot?",
                                "Use constructor injection and avoid field injection.",
                SourceType.STACKOVERFLOW,
                42,
                true,
                888L,
                1.55,
                Instant.parse("2025-12-01T00:00:00Z"),
                List.of("spring-boot", "dependency-injection"),
                "{\"score\":42,\"answered\":true}"
        );

        when(stackOverflowSearchClient.search(anyString(), anyInt(), anyInt(), anyString(), anyList()))
                .thenReturn(new ProviderSearchPage(List.of(sample), false));

        mockMvc.perform(get("/api/search")
                        .param("q", "spring boot dependency injection")
                        .param("sort", "relevance")
                        .param("limit", "10")
                        .param("offset", "0")
                        .param("tags", "spring-boot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("spring boot dependency injection"))
                .andExpect(jsonPath("$.sort").value("relevance"))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.providerHasMore").value(false))
                .andExpect(jsonPath("$.items[0].questionId").value(12345))
                .andExpect(jsonPath("$.items[0].accepted").value(true))
                .andExpect(jsonPath("$.items[0].answered").value(true))
                .andExpect(jsonPath("$.items[0].source").value("STACKOVERFLOW"));
    }

    @Test
        void searchEndpoint_usesProviderSnippetNotGenericPlaceholder() throws Exception {
        ProviderSearchResult sample = new ProviderSearchResult(
                456L,
                "https://stackoverflow.com/questions/456/example",
                                "Spring & Boot: can't parse UTF-8?",
                                "Use HttpMessageConverter with UTF-8 and decode entities.",
                SourceType.STACKOVERFLOW,
                30,
                true,
                777L,
                1.4,
                Instant.parse("2025-12-05T00:00:00Z"),
                List.of("spring-boot"),
                "{\"score\":30,\"answered\":true}"
        );

        when(stackOverflowSearchClient.search(anyString(), anyInt(), anyInt(), anyString(), anyList()))
                .thenReturn(new ProviderSearchPage(List.of(sample), false));

        mockMvc.perform(get("/api/search")
                        .param("q", "spring utf8 parse")
                        .param("sort", "relevance")
                        .param("limit", "10")
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Spring & Boot: can't parse UTF-8?"))
                .andExpect(jsonPath("$.items[0].snippet").value("Use HttpMessageConverter with UTF-8 and decode entities."));
    }

    @Test
    void analyticsEndpoint_returnsSummary() throws Exception {
        ProviderSearchResult sample = new ProviderSearchResult(
                999L,
                "https://stackoverflow.com/questions/999/example",
                "How to configure Redis cache?",
                "Use RedisTemplate with proper TTL.",
                SourceType.STACKOVERFLOW,
                20,
                true,
                null,
                1.2,
                Instant.now(),
                List.of("redis", "spring"),
                "{\"score\":20}"
        );

        when(stackOverflowSearchClient.search(anyString(), anyInt(), anyInt(), anyString(), anyList()))
                .thenReturn(new ProviderSearchPage(List.of(sample), false));

        mockMvc.perform(get("/api/search")
                        .param("q", "redis cache spring")
                        .param("limit", "10")
                        .param("offset", "0")
                        .param("sort", "relevance"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQueries").value(1))
                .andExpect(jsonPath("$.queriesLast24Hours").value(1))
                .andExpect(jsonPath("$.topQueries[0].query").value("redis cache spring"));
    }
}
