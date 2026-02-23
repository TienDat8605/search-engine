package com.searchengine.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

            List<ScoredItem> scored = new ArrayList<>(candidates.size());
            for (SearchItem item : candidates) {
                double vectorScore = 0.0;
                try {
                    Optional<String> embOpt = documentRepository.findEmbeddingByUrl(item.link());
                    if (embOpt.isPresent() && embOpt.get() != null) {
                        float[] docVec = EmbeddingService.parseEmbedding(embOpt.get());
                        if (docVec != null) {
                            vectorScore = cosineSimilarity(queryVec, docVec);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not get embedding for {}: {}", item.link(), e.getMessage());
                }
                // Blend: 60% existing score + 40% vector similarity (normalized to comparable range)
                double blendedScore = item.score() * 0.6 + vectorScore * 2.0 * 0.4;
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

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ScoredItem(SearchItem item, double blendedScore) {}
}
