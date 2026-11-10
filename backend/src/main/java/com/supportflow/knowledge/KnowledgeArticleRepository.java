package com.supportflow.knowledge;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long> {
    Optional<KnowledgeArticle> findByArticleKey(String articleKey);
    List<KnowledgeArticle> findByPublishedTrueOrderByUpdatedAtDesc();
}
