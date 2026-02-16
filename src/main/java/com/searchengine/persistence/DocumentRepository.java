package com.searchengine.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {
	Optional<DocumentEntity> findByQuestionId(Long questionId);
}
