package com.searchengine.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.searchengine.api.dto.SearchItem;
import com.searchengine.persistence.DocumentEntity;
import com.searchengine.persistence.DocumentRepository;

@Service
public class VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

    private final EmbeddingService embeddingService;
    private final DocumentRepository documentRepository;

    public VectorSearchService(EmbeddingService embeddingService, DocumentRepository documentRepository) {
        this.embeddingService = embeddingService;
        this.documentRepository = documentRepository;
    }

    /**
     * Finds documents similar to the document identified by questionId (excluding itself).
     */
    public List<DocumentEntity> findSimilar(Long questionId, int limit) {
        if (!embeddingService.isEnabled()) {
            return List.of();
        }
        try {
            Optional<DocumentEntity> docOpt = documentRepository.findByQuestionId(questionId);
            if (docOpt.isEmpty()) {
                return List.of();
            }
            DocumentEntity doc = docOpt.get();

            Optional<String> embOpt = documentRepository.findEmbeddingByQuestionId(questionId);
            if (embOpt.isEmpty() || embOpt.get() == null) {
                return List.of();
            }

            String embedding = embOpt.get();
            return documentRepository.findSimilarByEmbedding(embedding, limit + 1)
                    .stream()
                    .filter(e -> !e.getUrl().equals(doc.getUrl()))
                    .limit(limit)
                    .toList();
        } catch (Exception e) {
            log.warn("findSimilar failed for questionId {}: {}", questionId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Reranks a list of SearchItems by blending their existing scores with vector cosine similarity
     * to the query embedding.
     */
    public List<SearchItem> rerank(String query, List<SearchItem> candidates) {
        if (!embeddingService.isEnabled() || candidates == null || candidates.isEmpty()) {
            return candidates;
        }
        try {
            float[] queryVec = embeddingService.generateForQuery(query);
            if (queryVec == null) {
                return candidates;
            }

            // Batch fetch title similarity (1st priority) + answer similarity (2nd priority)
            String queryEmbeddingStr = EmbeddingService.formatEmbedding(queryVec);
            List<String> urls = candidates.stream().map(SearchItem::link).toList();

            Map<String, Double> titleSimMap = new java.util.HashMap<>();
            Map<String, Double> answerSimMap = new java.util.HashMap<>();
            try {
                List<DocumentRepository.UrlSemanticScoreView> rows =
                    documentRepository.findSemanticScoresByUrls(urls, queryEmbeddingStr);
                for (DocumentRepository.UrlSemanticScoreView row : rows) {
                    titleSimMap.put(row.getUrl(), row.getTitleSimilarity());
                    answerSimMap.put(row.getUrl(), row.getAnswerSimilarity());
                }
            } catch (Exception e) {
                log.warn("Batch semantic score query failed: {}", e.getMessage());
            }

            List<ScoredItem> scored = new ArrayList<>(candidates.size());
            for (SearchItem item : candidates) {
                double titleSim = titleSimMap.getOrDefault(item.link(), 0.0);
                double answerSim = answerSimMap.getOrDefault(item.link(), 0.0);
                // 70% title match (user query ≈ question title), 30% answer match
                double semanticScore = 0.7 * titleSim + 0.3 * answerSim;
                double blendedScore = item.score() * 0.6 + semanticScore * 2.0 * 0.4;
                scored.add(new ScoredItem(item, blendedScore));
            }

            return scored.stream()
                    .sorted(Comparator.comparingDouble(ScoredItem::blendedScore).reversed())
                    .map(s -> new SearchItem(
                            s.item().questionId(),
                            s.item().title(),
                            s.item().source(),
                            s.item().tags(),
                            s.item().questionScore(),
                            s.item().answered(),
                            s.item().accepted(),
                            s.item().snippet(),
                            s.item().link(),
                            s.blendedScore()
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("rerank failed: {}", e.getMessage());
            return candidates;
        }
    }

    private record ScoredItem(SearchItem item, double blendedScore) {}
}
