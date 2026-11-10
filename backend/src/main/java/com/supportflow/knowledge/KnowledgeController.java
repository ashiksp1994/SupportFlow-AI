package com.supportflow.knowledge;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

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
        return knowledgeService.getById(id);
    }

    @GetMapping("/articles/{id}/chunks")
    public List<KnowledgeChunk> getArticleChunks(@PathVariable Long id) {
        return knowledgeService.getChunksForArticle(id);
    }

    @PostMapping("/articles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public KnowledgeArticle createArticle(
            @Valid @RequestBody KnowledgeService.CreateArticleRequest request,
            Authentication auth) {
        String actor = auth != null ? auth.getName() : "SYSTEM";
        return knowledgeService.createArticle(request, actor);
    }

    @GetMapping("/search")
    public List<KnowledgeService.KnowledgeSearchResult> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "5") int limit) {
        String effectiveQuery = (query != null && !query.isBlank()) ? query : (q != null ? q : "");
        return knowledgeService.search(effectiveQuery, limit);
    }
}
