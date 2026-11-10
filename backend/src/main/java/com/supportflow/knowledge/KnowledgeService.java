package com.supportflow.knowledge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotBlank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.audit.AuditService;
import com.supportflow.identity.security.AccessControlService;

/**
 * Knowledge Base & Grounded RAG Retrieval Service.
 * Segments articles into searchable chunks, computes 16-dimensional semantic feature + lexical embeddings,
 * enforces multi-organisation visibility, and filters out results below the minimum relevance threshold
 * so that AI suggestions never fabricate citations when the KB lacks evidence.
 */
@Service
public class KnowledgeService {

    private static final List<String[]> SEMANTIC_DIMENSIONS = List.of(
            new String[]{"sso", "saml", "oidc", "oauth", "login", "authentication", "mfa", "password", "token", "jwt", "certificate", "idp", "okta", "entra"},
            new String[]{"billing", "invoice", "refund", "credit", "subscription", "charge", "stripe", "payment", "plan", "vat", "pricing"},
            new String[]{"api", "webhook", "rate", "limit", "429", "500", "timeout", "sdk", "endpoint", "payload", "rest", "graphql"},
            new String[]{"sla", "outage", "critical", "incident", "downtime", "latency", "degraded", "escalation", "production", "p1"},
            new String[]{"database", "postgres", "pgvector", "migration", "backup", "replication", "query", "index", "lock", "connection"},
            new String[]{"security", "rbac", "permission", "role", "audit", "compliance", "gdpr", "encryption", "tls", "vulnerability"},
            new String[]{"rag", "ai", "embedding", "vector", "knowledge", "citation", "agent", "approval", "model", "prompt"},
            new String[]{"ui", "frontend", "browser", "dashboard", "export", "csv", "notification", "email", "slack", "attachment"},
            new String[]{"certificate", "x509", "pem", "signature", "401", "rotation", "metadata", "assertion", "signing"},
            new String[]{"retry", "backoff", "jitter", "idempotency", "hmac", "sha256", "delivery", "queue", "dlq"},
            new String[]{"credit", "pro-rata", "uptime", "breach", "tier", "enterprise", "business", "contract"},
            new String[]{"tenant", "organisation", "isolation", "cross-tenant", "customer", "workspace", "domain"}
    );

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "are", "was", "were", "have", "has",
            "had", "not", "but", "what", "all", "when", "can", "your", "our", "how", "why", "who", "into", "after"
    );

    public record CreateArticleRequest(
            @NotBlank(message = "Title is required") String title,
            @NotBlank(message = "Category is required") String category,
            String tags,
            @NotBlank(message = "Content is required") String content,
            Long organisationId
    ) {
        public CreateArticleRequest(String title, String category, String tags, String content) {
            this(title, category, tags, content, null);
        }
    }

    public record KnowledgeSearchResult(
            Long id,
            String articleKey,
            String title,
            String category,
            String tags,
            String snippet,
            double relevanceScore
    ) {}

    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final AuditService auditService;
    private final AccessControlService accessControlService;
    private final double minRelevanceThreshold;

    public KnowledgeService(
            KnowledgeArticleRepository articleRepository,
            KnowledgeChunkRepository chunkRepository,
            AuditService auditService,
            AccessControlService accessControlService,
            @Value("${app.ai.rag.min-relevance-threshold:0.25}") double minRelevanceThreshold) {
        this.articleRepository = articleRepository;
        this.chunkRepository = chunkRepository;
        this.auditService = auditService;
        this.accessControlService = accessControlService;
        this.minRelevanceThreshold = minRelevanceThreshold;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeArticle> listPublishedArticles() {
        return articleRepository.findByPublishedTrueOrderByUpdatedAtDesc().stream()
                .filter(this::isArticleVisibleToCaller)
                .toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeArticle getById(Long id) {
        KnowledgeArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge article not found: " + id));
        if (!isArticleVisibleToCaller(article)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to organisation knowledge article.");
        }
        return article;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeChunk> getChunksForArticle(Long articleId) {
        getById(articleId);
        return chunkRepository.findByArticleIdOrderByChunkIndexAsc(articleId);
    }

    @Transactional
    public KnowledgeArticle createArticle(CreateArticleRequest request, String actor) {
        accessControlService.requireStaff();
        KnowledgeArticle article = new KnowledgeArticle();
        long nextNum = articleRepository.count() + 101;
        article.setArticleKey("KB-" + nextNum);
        article.setTitle(request.title().trim());
        article.setCategory(request.category().trim().toUpperCase());
        article.setTags(request.tags() != null ? request.tags().trim().toLowerCase() : "");
        article.setContent(request.content().trim());
        article.setOrganisationId(request.organisationId());
        article.setCreatedBy(actor);
        article.setPublished(true);

        String combinedText = article.getTitle() + " " + article.getCategory() + " " + article.getTags() + " " + article.getContent();
        article.setEmbeddingVector(computeEmbedding(combinedText));

        KnowledgeArticle saved = articleRepository.save(article);

        // Segment article content into grounded chunks and index each chunk's vector
        List<String> segments = chunkContent(saved.getTitle(), saved.getContent());
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setArticleId(saved.getId());
            chunk.setArticleKey(saved.getArticleKey());
            chunk.setOrganisationId(saved.getOrganisationId());
            chunk.setChunkIndex(i);
            chunk.setChunkText(segments.get(i));
            chunk.setEmbeddingVector(computeEmbedding(saved.getTitle() + " " + saved.getTags() + " " + segments.get(i)));
            chunks.add(chunk);
        }
        chunkRepository.saveAll(chunks);

        auditService.recordEvent("KB_ARTICLE_PUBLISHED", actor, "KB_ARTICLE", saved.getArticleKey(),
                "Published knowledge base article '" + saved.getTitle() + "' with " + chunks.size() + " RAG chunks");
        return saved;
    }

    /**
     * Performs tenant-filtered hybrid vector + lexical chunk search with strict minimum relevance threshold.
     * Never returns articles below {@code minRelevanceThreshold} so downstream RAG cannot hallucinate citations.
     */
    @Transactional(readOnly = true)
    public List<KnowledgeSearchResult> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<KnowledgeArticle> visibleArticles = listPublishedArticles();
        if (visibleArticles.isEmpty()) {
            return List.of();
        }

        Map<Long, KnowledgeArticle> articleMap = visibleArticles.stream()
                .collect(Collectors.toMap(KnowledgeArticle::getId, a -> a));

        double[] queryVec = parseVector(computeEmbedding(query));
        List<String> meaningfulTokens = Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(t -> t.length() >= 3 && !STOP_WORDS.contains(t))
                .distinct()
                .toList();

        if (meaningfulTokens.isEmpty()) {
            return List.of();
        }

        List<KnowledgeSearchResult> scored = new ArrayList<>();

        for (KnowledgeArticle article : visibleArticles) {
            List<KnowledgeChunk> articleChunks = chunkRepository.findByArticleIdOrderByChunkIndexAsc(article.getId());
            String bestSnippet = summarizeSnippet(article.getContent());
            double bestChunkCosine = cosineSimilarity(queryVec, parseVector(article.getEmbeddingVector()));

            for (KnowledgeChunk chunk : articleChunks) {
                double chunkCosine = cosineSimilarity(queryVec, parseVector(chunk.getEmbeddingVector()));
                double chunkLexical = computeLexicalOverlap(meaningfulTokens, article.getTitle(), article.getTags(), chunk.getChunkText());
                if (chunkCosine + chunkLexical > bestChunkCosine) {
                    bestChunkCosine = chunkCosine;
                    bestSnippet = summarizeSnippet(chunk.getChunkText());
                }
            }

            double lexicalScore = computeLexicalOverlap(
                    meaningfulTokens,
                    article.getTitle(),
                    article.getTags(),
                    article.getContent());

            // Require at least some lexical or strong semantic signal; never clamp to an artificial floor.
            double combinedScore = Math.min(0.99, (bestChunkCosine * 0.50) + (lexicalScore * 0.50));
            if (lexicalScore == 0.0 && bestChunkCosine < 0.65) {
                combinedScore = 0.0;
            }

            if (combinedScore >= minRelevanceThreshold) {
                scored.add(new KnowledgeSearchResult(
                        article.getId(),
                        article.getArticleKey(),
                        article.getTitle(),
                        article.getCategory(),
                        article.getTags(),
                        bestSnippet,
                        Math.round(combinedScore * 100.0) / 100.0
                ));
            }
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(KnowledgeSearchResult::relevanceScore).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    private boolean isArticleVisibleToCaller(KnowledgeArticle article) {
        if (article.getOrganisationId() == null) {
            return true;
        }
        return accessControlService.canAccessTenant(article.getOrganisationId(), null);
    }

    private double computeLexicalOverlap(List<String> queryTokens, String title, String tags, String body) {
        if (queryTokens.isEmpty()) {
            return 0.0;
        }
        String titleLower = title != null ? title.toLowerCase(Locale.ROOT) : "";
        String tagsLower = tags != null ? tags.toLowerCase(Locale.ROOT) : "";
        String bodyLower = body != null ? body.toLowerCase(Locale.ROOT) : "";

        double matchedWeight = 0.0;
        for (String token : queryTokens) {
            if (titleLower.contains(token) || tagsLower.contains(token)) {
                matchedWeight += 1.0;
            } else if (bodyLower.contains(token)) {
                matchedWeight += 0.65;
            }
        }
        return Math.min(1.0, matchedWeight / Math.min(queryTokens.size(), 4));
    }

    public List<String> chunkContent(String title, String content) {
        if (content == null || content.isBlank()) {
            return List.of(title != null ? title : "");
        }
        String[] paragraphs = content.split("\\n\\s*\\n|(?<=\\.)\\s+(?=[A-Z0-9])");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String p : paragraphs) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (current.length() + trimmed.length() > 360 && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(" ");
            }
            current.append(trimmed);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return chunks.isEmpty() ? List.of(content.trim()) : chunks;
    }

    public String computeEmbedding(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        double[] vec = new double[SEMANTIC_DIMENSIONS.size()];
        double norm = 0.0;

        for (int i = 0; i < SEMANTIC_DIMENSIONS.size(); i++) {
            double score = 0.0;
            for (String kw : SEMANTIC_DIMENSIONS.get(i)) {
                if (lower.contains(kw)) {
                    score += 1.0;
                }
            }
            vec[i] = score;
            norm += score * score;
        }

        double denom = norm > 0 ? Math.sqrt(norm) : 1.0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(String.format(Locale.ROOT, "%.4f", vec[i] / denom));
        }
        return sb.toString();
    }

    private double[] parseVector(String vectorStr) {
        if (vectorStr == null || vectorStr.isBlank()) {
            return new double[SEMANTIC_DIMENSIONS.size()];
        }
        String[] parts = vectorStr.split(",");
        double[] vec = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                vec[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException ignored) {
                vec[i] = 0.0;
            }
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
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String summarizeSnippet(String content) {
        if (content == null) {
            return "";
        }
        String clean = content.replaceAll("\\s+", " ").trim();
        return clean.length() <= 220 ? clean : clean.substring(0, 217) + "...";
    }
}
