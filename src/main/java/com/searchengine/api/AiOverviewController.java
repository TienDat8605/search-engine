package com.searchengine.api;

import com.searchengine.api.dto.AiOverviewResponse;
import com.searchengine.api.dto.SearchItem;
import com.searchengine.api.dto.SearchResponse;
import com.searchengine.persistence.DocumentEntity;
import com.searchengine.persistence.DocumentRepository;
import com.searchengine.service.AiCacheService;
import com.searchengine.service.AiOverviewService;
import com.searchengine.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AiOverviewController {

    private static final Logger log = LoggerFactory.getLogger(AiOverviewController.class);

    private final AiOverviewService aiOverviewService;
    private final AiCacheService aiCacheService;
    private final SearchService searchService;
    private final DocumentRepository documentRepository;

    public AiOverviewController(
            AiOverviewService aiOverviewService,
            AiCacheService aiCacheService,
            SearchService searchService,
            DocumentRepository documentRepository) {
        this.aiOverviewService = aiOverviewService;
        this.aiCacheService = aiCacheService;
        this.searchService = searchService;
        this.documentRepository = documentRepository;
    }

    @GetMapping("/ask")
    public ResponseEntity<AiOverviewResponse> ask(@RequestParam("q") String query) {
        if (!aiOverviewService.isEnabled()) {
            return ResponseEntity.ok(AiOverviewResponse.empty());
        }
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(AiOverviewResponse.empty());
        }

        String normalizedQuery = query.trim().toLowerCase();

        // Check Redis cache first
        Optional<AiOverviewResponse> cached = aiCacheService.get(normalizedQuery);
        if (cached.isPresent()) {
            log.debug("AI overview cache hit for: {}", normalizedQuery);
            return ResponseEntity.ok(cached.get());
        }

        // Fetch search results to get candidate URLs (uses search cache if warm)
        SearchResponse searchResponse = searchService.search(normalizedQuery, 5, 0, "votes", List.of());
        List<String> urls = searchResponse.items().stream().map(SearchItem::link).toList();

        // Load enriched DocumentEntities from DB
        List<DocumentEntity> docs = documentRepository.findAllById(urls);

        // Generate AI overview grounded in these documents
        AiOverviewResponse response = aiOverviewService.generate(normalizedQuery, docs);

        // Cache even empty responses to avoid hammering the LLM on repeated queries
        if (response != null && !response.answer().isBlank()) {
            aiCacheService.put(normalizedQuery, response);
        }

        return ResponseEntity.ok(response != null ? response : AiOverviewResponse.empty());
    }
}
