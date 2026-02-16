package com.searchengine.api;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.searchengine.api.dto.DocumentResponse;
import com.searchengine.persistence.DocumentEntity;
import com.searchengine.persistence.DocumentRepository;

@RestController
@RequestMapping("/api")
public class DocumentController {

    private final DocumentRepository documentRepository;

    public DocumentController(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @GetMapping("/doc/{questionId}")
    public DocumentResponse getDocument(@PathVariable("questionId") Long questionId) {
        DocumentEntity entity = documentRepository.findByQuestionId(questionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        return new DocumentResponse(
                entity.getQuestionId(),
                entity.getTitle(),
                entity.getUrl(),
                entity.getSource(),
                splitTags(entity.getTags()),
                entity.getQuestionText(),
                entity.getBestAnswerText(),
                entity.getMetadataJson(),
                entity.getFetchedAt()
        );
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
