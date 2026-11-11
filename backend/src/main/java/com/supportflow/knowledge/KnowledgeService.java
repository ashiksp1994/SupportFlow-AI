package com.supportflow.knowledge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.audit.AuditService;
import com.supportflow.identity.security.AccessControlService;

/**
 * Knowledge Base & Grounded RAG Retrieval Service.
 * Segments articles into searchable chunks, computes embeddings via {@link EmbeddingProviderClient}
 * (supporting external OpenAI-compatible embeddings or deterministic 12-dimension concept vectors
 * with hybrid lexical overlap scoring), enforces multi-organisation visibility, and filters out
 * results below the minimum relevance threshold so AI suggestions never fabricate citations.
 */
@Service
public class KnowledgeService {

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
            double relevanceScore,
            String embeddingProvider,
            boolean fallbackUsed
    ) {
        public KnowledgeSearchResult(
                Long id,
                String articleKey,
                String title,
                String category,
                String tags,
                String snippet,
                double relevanceScore) {
            this(id, articleKey, title, category, tags, snippet, relevanceScore, "local-hybrid-lexical", true);
        }
    }

    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final AuditService auditService;
    private final AccessControlService accessControlService;
    private final EmbeddingProviderClient embeddingProviderClient;
    private final double minRelevanceThreshold;

    @Autowired
    public KnowledgeService(
            KnowledgeArticleRepository articleRepository,
            KnowledgeChunkRepository chunkRepository,
            AuditService auditService,
            AccessControlService accessControlService,
            EmbeddingProviderClient embeddingProviderClient,
            @Value("${app.ai.rag.min-relevance-threshold:0.25}") double minRelevanceThreshold) {
        this.articleRepository = articleRepository;
        this.chunkRepository = chunkRepository;
        this.auditService = auditService;
        this.accessControlService = accessControlService;
        this.embeddingProviderClient = embeddingProviderClient;
        this.minRelevanceThreshold = minRelevanceThreshold;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeArticle> listPublishedArticles() {
        accessControlService.requireAuthenticated();
        return articleRepository.findByPublishedTrueOrderByUpdatedAtDesc().stream()
                .filter(this::isArticleVisibleToCaller)
                .toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeArticle getById(Long id) {
        accessControlService.requireAuthenticated();
        KnowledgeArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge article not found: " + id));
        if (!isArticleVisibleToCaller(article)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to organisation knowledge article.");
        }
        return article;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeChunk> getChunksForArticle(Long id) {
        getById(id);
        return chunkRepository.findByArticleIdOrderByChunkIndexAsc(id);
    }

    @Transactional
    public KnowledgeArticle createArticle(CreateArticleRequest request, String actor) {
        accessControlService.requireStaff();
        KnowledgeArticle article = new KnowledgeArticle();
        long nextNum = articleRepository.count() + 101;
        article.setArticleKey("KB-" + nextNum);
        article.setTitle(request.title().trim());
        article.setCategory(request.category().trim().toUpperCase(Locale.ROOT));
        article.setTags(request.tags() != null ? request.tags().trim().toLowerCase(Locale.ROOT) : "");
        article.setContent(request.content().trim());
        article.setOrganisationId(request.organisationId());
        article.setCreatedBy(actor);
        article.setPublished(true);

        String combinedText = article.getTitle() + " " + article.getCategory() + " " + article.getTags() + " " + article.getContent();
        article.setEmbeddingVector(computeEmbedding(combinedText));

        KnowledgeArticle saved = articleRepository.save(article);

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

        EmbeddingProviderClient.EmbeddingResult queryEmbedding = embeddingProviderClient.embedWithFallback(query);
        double[] queryVec = parseVector(queryEmbedding.vectorCsv());
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
                        Math.round(combinedScore * 100.0) / 100.0,
                        queryEmbedding.provider(),
                        queryEmbedding.fallbackUsed()
                ));
            }
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(KnowledgeSearchResult::relevanceScore).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    private boolean isArticleVisibleToCaller(KnowledgeArticle article) {
        AccessControlService.CallerContext caller = accessControlService.requireAuthenticated();
        if (article.getOrganisationId() == null) {
            // Global runbooks are visible to all authenticated users unless a tenant-bound agent is restricted to tenant-only view
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
        return embeddingProviderClient.embedWithFallback(text).vectorCsv();
    }

    private double[] parseVector(String vectorStr) {
        if (vectorStr == null || vectorStr.isBlank()) {
            return new double[EmbeddingProviderClient.defaultDimensionCount()];
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
