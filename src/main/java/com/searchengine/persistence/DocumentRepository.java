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

    interface UrlSimilarityView {
        String getUrl();
        double getSimilarity();
    }

    @Query(value = "SELECT url, 1 - (embedding <=> CAST(:queryEmbedding AS vector)) AS similarity FROM documents WHERE url IN (:urls) AND embedding IS NOT NULL", nativeQuery = true)
    List<UrlSimilarityView> findSimilaritiesByUrls(@Param("urls") List<String> urls, @Param("queryEmbedding") String queryEmbedding);

    @Modifying
    @Transactional
    @Query(value = "UPDATE documents SET title_embedding = CAST(:embedding AS vector) WHERE url = :url", nativeQuery = true)
    void updateTitleEmbedding(@Param("url") String url, @Param("embedding") String embedding);

    @Modifying
    @Transactional
    @Query(value = "UPDATE documents SET answer_embedding = CAST(:embedding AS vector) WHERE url = :url", nativeQuery = true)
    void updateAnswerEmbedding(@Param("url") String url, @Param("embedding") String embedding);

    interface UrlSemanticScoreView {
        String getUrl();
        double getTitleSimilarity();
        double getAnswerSimilarity();
    }

    @Query(value = """
        SELECT
            url,
            CASE WHEN title_embedding IS NOT NULL
                 THEN 1 - (title_embedding <=> CAST(:queryEmbedding AS vector)) ELSE 0 END AS title_similarity,
            CASE WHEN answer_embedding IS NOT NULL
                 THEN 1 - (answer_embedding <=> CAST(:queryEmbedding AS vector)) ELSE 0 END AS answer_similarity
        FROM documents
        WHERE url IN (:urls)
        """, nativeQuery = true)
    List<UrlSemanticScoreView> findSemanticScoresByUrls(@Param("urls") List<String> urls, @Param("queryEmbedding") String queryEmbedding);
}
