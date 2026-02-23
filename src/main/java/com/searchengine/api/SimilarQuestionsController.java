package com.searchengine.api;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.searchengine.api.dto.SimilarQuestion;
import com.searchengine.persistence.DocumentEntity;
import com.searchengine.persistence.DocumentRepository;
import com.searchengine.service.VectorSearchService;

@RestController
@RequestMapping("/api")
public class SimilarQuestionsController {

    private static final int DEFAULT_LIMIT = 5;

    private final VectorSearchService vectorSearchService;
    private final DocumentRepository documentRepository;

    public SimilarQuestionsController(
            VectorSearchService vectorSearchService,
            DocumentRepository documentRepository
    ) {
        this.vectorSearchService = vectorSearchService;
        this.documentRepository = documentRepository;
    }

    @GetMapping("/similar/{questionId}")
    public ResponseEntity<List<SimilarQuestion>> findSimilar(
            @PathVariable String questionId,
            @RequestParam(defaultValue = "5") int limit
    ) {
        Long id;
        try {
            id = Long.parseLong(questionId);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().build();
        }

        if (documentRepository.findByQuestionId(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        int safeLimit = Math.min(Math.max(1, limit), DEFAULT_LIMIT);
        List<DocumentEntity> similar = vectorSearchService.findSimilar(id, safeLimit);

        List<SimilarQuestion> response = similar.stream()
                .map(e -> new SimilarQuestion(
                        e.getQuestionId(),
                        e.getTitle(),
                        e.getUrl(),
                        truncate(e.getNormalizedText(), 300),
                        parseTags(e.getTags())
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(t -> !t.isBlank())
                .toList();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength).trim() + "...";
    }
}
