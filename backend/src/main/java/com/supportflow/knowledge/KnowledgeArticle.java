package com.supportflow.knowledge;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "knowledge_articles")
public class KnowledgeArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_key", nullable = false, unique = true, length = 32)
    private String articleKey;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(length = 512)
    private String tags;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "embedding_vector", nullable = false, columnDefinition = "TEXT")
    private String embeddingVector;

    @Column(name = "organisation_id")
    private Long organisationId;

    @Column(nullable = false)
    private boolean published = true;

    @Column(name = "view_count", nullable = false)
    private long viewCount = 0;

    @Column(name = "created_by", nullable = false)
    private String createdBy = "SYSTEM";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
