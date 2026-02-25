package com.searchengine.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.searchengine.config.SearchProperties;
import com.searchengine.integration.EmbeddingClient;
import com.searchengine.persistence.DocumentEntity;
import com.searchengine.persistence.DocumentRepository;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingClient embeddingClient;
    private final DocumentRepository documentRepository;
    private final Executor embeddingExecutor;
    private final SearchProperties searchProperties;

    public EmbeddingService(
            EmbeddingClient embeddingClient,
            DocumentRepository documentRepository,
            @Qualifier("embeddingExecutor") Executor embeddingExecutor,
            SearchProperties searchProperties
    ) {
        this.embeddingClient = embeddingClient;
        this.documentRepository = documentRepository;
        this.embeddingExecutor = embeddingExecutor;
        this.searchProperties = searchProperties;
    }

    public boolean isEnabled() {
        return embeddingClient.isEnabled();
    }

    /**
     * Submits an async task to generate and persist embedding for the given document.
     */
    public void generateAndStore(DocumentEntity entity) {
        if (!embeddingClient.isEnabled()) {
            return;
        }
        CompletableFuture.runAsync(() -> doGenerateAndStore(entity), embeddingExecutor);
    }

    /**
     * Generates and persists separate title and answer embeddings for semantic reranking.
     * Title gets first priority (question-to-question semantic match),
     * answer gets second priority (question-to-answer match).
     */
    public void generateAndStoreTitleAndAnswer(DocumentEntity entity) {
        if (!embeddingClient.isEnabled()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            String url = entity.getUrl();
            try {
                if (entity.getTitle() != null && !entity.getTitle().isBlank()) {
                    float[] titleVec = embeddingClient.embed(entity.getTitle());
                    if (titleVec != null) {
                        documentRepository.updateTitleEmbedding(url, formatEmbedding(titleVec));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to generate title embedding for {}: {}", url, e.getMessage());
            }
            try {
                if (entity.getBestAnswerText() != null && !entity.getBestAnswerText().isBlank()) {
                    float[] answerVec = embeddingClient.embed(entity.getBestAnswerText());
                    if (answerVec != null) {
                        documentRepository.updateAnswerEmbedding(url, formatEmbedding(answerVec));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to generate answer embedding for {}: {}", url, e.getMessage());
            }
        }, embeddingExecutor);
    }

    /**
     * Returns an embedding for the given query text without storing it.
     */
    public float[] generateForQuery(String query) {
        if (!embeddingClient.isEnabled() || query == null || query.isBlank()) {
            return null;
        }
        return embeddingClient.embed(query);
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void processUnembeddedDocuments() {
        if (!embeddingClient.isEnabled()) {
            return;
        }
        int batchSize = searchProperties.getEmbedding().getBatchSize();
        try {
            List<DocumentEntity> unembedded = documentRepository.findUnembeddedWithContent(batchSize);
            if (unembedded.isEmpty()) {
                return;
            }
            log.info("Processing {} unembedded documents", unembedded.size());

            List<String> texts = unembedded.stream()
                    .map(this::buildText)
                    .toList();

            List<float[]> embeddings = embeddingClient.embedBatch(texts);

            List<String> urls = new ArrayList<>();
            List<String> embStrings = new ArrayList<>();
            for (int i = 0; i < unembedded.size(); i++) {
                float[] vec = (i < embeddings.size()) ? embeddings.get(i) : null;
                if (vec != null) {
                    urls.add(unembedded.get(i).getUrl());
                    embStrings.add(formatEmbedding(vec));
                }
            }

            for (int i = 0; i < urls.size(); i++) {
                try {
                    documentRepository.updateEmbedding(urls.get(i), embStrings.get(i));
                } catch (Exception e) {
                    log.warn("Failed to save embedding for url {}: {}", urls.get(i), e.getMessage());
                }
            }

            log.info("Stored embeddings for {}/{} documents", urls.size(), unembedded.size());
        } catch (Exception e) {
            log.warn("processUnembeddedDocuments failed: {}", e.getMessage());
        }
    }

    private void doGenerateAndStore(DocumentEntity entity) {
        try {
            String text = buildText(entity);
            if (text.isBlank()) {
                return;
            }
            float[] vec = embeddingClient.embed(text);
            if (vec == null) {
                return;
            }
            documentRepository.updateEmbedding(entity.getUrl(), formatEmbedding(vec));
        } catch (Exception e) {
            log.warn("Failed to generate/store embedding for {}: {}", entity.getUrl(), e.getMessage());
        }
    }

    private String buildText(DocumentEntity entity) {
        StringBuilder sb = new StringBuilder();
        if (entity.getTitle() != null && !entity.getTitle().isBlank()) {
            sb.append(entity.getTitle());
        }
        if (entity.getQuestionText() != null && !entity.getQuestionText().isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(entity.getQuestionText());
        }
        if (entity.getBestAnswerText() != null && !entity.getBestAnswerText().isBlank()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(entity.getBestAnswerText());
        }
        return sb.toString().trim();
    }

    static String formatEmbedding(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    static float[] parseEmbedding(String embeddingStr) {
        if (embeddingStr == null || embeddingStr.isBlank()) {
            return null;
        }
        try {
            String cleaned = embeddingStr.trim().replaceAll("[\\[\\]]", "");
            String[] parts = cleaned.split(",");
            float[] result = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Float.parseFloat(parts[i].trim());
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }
}
