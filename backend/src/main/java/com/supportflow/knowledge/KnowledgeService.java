package com.supportflow.knowledge;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.audit.AuditService;

@Service
public class KnowledgeService {

    private static final String[][] SEMANTIC_DIMENSIONS = {
            {"saml", "sso", "idp", "certificate", "assertion", "okta", "auth", "login", "mfa", "oauth"},
            {"billing", "invoice", "refund", "credit", "charge", "subscription", "payment", "stripe", "vat", "plan"},
            {"webhook", "api", "rate", "limit", "429", "timeout", "sdk", "payload", "integration", "endpoint"},
            {"database", "pgvector", "postgres", "latency", "query", "index", "migration", "connection", "pool", "slow"},
            {"sla", "escalation", "enterprise", "outage", "critical", "incident", "downtime", "breach", "uptime", "p1"},
            {"permission", "rbac", "role", "admin", "access", "forbidden", "403", "401", "token", "jwt"},
            {"export", "gdpr", "compliance", "audit", "privacy", "deletion", "retention", "soc2", "pii", "encryption"},
            {"attachment", "upload", "s3", "minio", "presigned", "file", "storage", "bucket", "media", "size"}
    };

    public record CreateArticleRequest(
            @NotBlank(message = "Title is required") String title,
            @NotBlank(message = "Category is required") String category,
            String tags,
            @NotBlank(message = "Content is required") String content
    ) {}

    public record KnowledgeSearchResult(
            Long id,
            String articleKey,
            String title,
            String category,
            String tags,
            double relevanceScore,
            String citationSnippet
    ) {}

    private final KnowledgeArticleRepository articleRepository;
    private final AuditService auditService;

    public KnowledgeService(KnowledgeArticleRepository articleRepository, AuditService auditService) {
        this.articleRepository = articleRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeArticle> listPublishedArticles() {
        return articleRepository.findByPublishedTrueOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public KnowledgeArticle findById(Long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge article not found: " + id));
    }

    @Transactional
    public KnowledgeArticle createArticle(CreateArticleRequest request, String actor) {
        KnowledgeArticle article = new KnowledgeArticle();
        long nextNum = articleRepository.count() + 101;
        article.setArticleKey("KB-" + nextNum);
        article.setTitle(request.title().trim());
        article.setCategory(request.category().trim().toUpperCase());
        article.setTags(request.tags() != null ? request.tags().trim() : "");
        article.setContent(request.content().trim());
        article.setCreatedBy(actor != null ? actor : "SYSTEM");

        String combinedText = article.getTitle() + " " + article.getCategory() + " " + article.getTags() + " " + article.getContent();
        article.setEmbeddingVector(formatVector(computeEmbedding(combinedText)));
        article.setUpdatedAt(Instant.now());

        KnowledgeArticle saved = articleRepository.save(article);
        auditService.recordEvent("KB_ARTICLE_CREATED", actor, "KNOWLEDGE_ARTICLE", saved.getArticleKey(),
                "Published knowledge base article '" + saved.getTitle() + "'");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeSearchResult> search(String query, int limit) {
        List<KnowledgeArticle> articles = articleRepository.findByPublishedTrueOrderByUpdatedAtDesc();
        if (query == null || query.isBlank()) {
            return articles.stream()
                    .limit(Math.max(1, limit))
                    .map(a -> new KnowledgeSearchResult(
                            a.getId(), a.getArticleKey(), a.getTitle(), a.getCategory(), a.getTags(),
                            1.0, extractSnippet(a.getContent(), "")))
                    .toList();
        }

        double[] queryVec = computeEmbedding(query);
        Set<String> queryTokens = tokenize(query);

        return articles.stream()
                .map(article -> {
                    double[] docVec = parseVector(article.getEmbeddingVector());
                    double cosine = cosineSimilarity(queryVec, docVec);

                    Set<String> docTokens = tokenize(article.getTitle() + " " + article.getTags() + " " + article.getContent());
                    long overlap = queryTokens.stream().filter(docTokens::contains).count();
                    double lexicalScore = queryTokens.isEmpty() ? 0.0 : (double) overlap / queryTokens.size();

                    double combinedScore = Math.min(0.99, Math.round((cosine * 0.65 + lexicalScore * 0.35) * 100.0) / 100.0);
                    return new KnowledgeSearchResult(
                            article.getId(),
                            article.getArticleKey(),
                            article.getTitle(),
                            article.getCategory(),
                            article.getTags(),
                            Math.max(0.15, combinedScore),
                            extractSnippet(article.getContent(), query)
                    );
                })
                .sorted(Comparator.comparingDouble(KnowledgeSearchResult::relevanceScore).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    public double[] computeEmbedding(String text) {
        double[] vec = new double[SEMANTIC_DIMENSIONS.length];
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        double normSq = 0.0;
        for (int i = 0; i < SEMANTIC_DIMENSIONS.length; i++) {
            double score = 0.05;
            for (String keyword : SEMANTIC_DIMENSIONS[i]) {
                if (lower.contains(keyword)) {
                    score += 1.0;
                }
            }
            vec[i] = score;
            normSq += score * score;
        }
        double norm = Math.sqrt(normSq);
        if (norm > 0) {
            for (int i = 0; i < vec.length; i++) {
                vec[i] = Math.round((vec[i] / norm) * 10000.0) / 10000.0;
            }
        }
        return vec;
    }

    private String formatVector(double[] vec) {
        return Arrays.stream(vec)
                .mapToObj(Double::toString)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private double[] parseVector(String formatted) {
        if (formatted == null || formatted.isBlank()) {
            return new double[SEMANTIC_DIMENSIONS.length];
        }
        String clean = formatted.replace("[", "").replace("]", "").trim();
        if (clean.isEmpty()) {
            return new double[SEMANTIC_DIMENSIONS.length];
        }
        String[] parts = clean.split(",");
        double[] vec = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Double.parseDouble(parts[i].trim());
        }
        return vec;
    }

    private double cosineSimilarity(double[] a, double[] b) {
        int len = Math.min(a.length, b.length);
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(t -> t.length() >= 3)
                .collect(Collectors.toSet());
    }

    private String extractSnippet(String content, String query) {
        if (content == null) return "";
        if (content.length() <= 220) return content;
        return content.substring(0, 217) + "...";
    }

    @RestController
    @RequestMapping("/api/knowledge")
    public static class KnowledgeController {

        private final KnowledgeService knowledgeService;

        public KnowledgeController(KnowledgeService knowledgeService) {
            this.knowledgeService = knowledgeService;
        }

        @GetMapping("/articles")
        public List<KnowledgeArticle> listArticles() {
            return knowledgeService.listPublishedArticles();
        }

        @GetMapping("/articles/{id}")
        public KnowledgeArticle getArticle(@PathVariable Long id) {
            return knowledgeService.findById(id);
        }

        @PostMapping("/articles")
        @ResponseStatus(HttpStatus.CREATED)
        public KnowledgeArticle createArticle(@Valid @RequestBody CreateArticleRequest request, Authentication auth) {
            String actor = auth != null ? auth.getName() : "SYSTEM";
            return knowledgeService.createArticle(request, actor);
        }

        @GetMapping("/search")
        public List<KnowledgeSearchResult> searchArticles(
                @RequestParam(name = "q", defaultValue = "") String query,
                @RequestParam(name = "limit", defaultValue = "4") int limit) {
            return knowledgeService.search(query, limit);
        }
    }
}
