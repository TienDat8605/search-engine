package com.searchengine.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    Optional<DocumentEntity> findByQuestionId(Long questionId);

    @Query(value = "SELECT * FROM documents WHERE embedding IS NULL AND (question_text IS NOT NULL AND question_text != '') LIMIT :limit", nativeQuery = true)
    List<DocumentEntity> findUnembeddedWithContent(@Param("limit") int limit);

    @Query(value = "SELECT * FROM documents WHERE embedding IS NOT NULL ORDER BY embedding <=> CAST(:queryEmbedding AS vector) LIMIT :limit", nativeQuery = true)
    List<DocumentEntity> findSimilarByEmbedding(@Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);

    @Query(value = "SELECT embedding::text FROM documents WHERE url = :url", nativeQuery = true)
    Optional<String> findEmbeddingByUrl(@Param("url") String url);

    @Query(value = "SELECT embedding::text FROM documents WHERE question_id = :questionId", nativeQuery = true)
    Optional<String> findEmbeddingByQuestionId(@Param("questionId") Long questionId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE documents SET embedding = CAST(:embedding AS vector) WHERE url = :url", nativeQuery = true)
    void updateEmbedding(@Param("url") String url, @Param("embedding") String embedding);
}
