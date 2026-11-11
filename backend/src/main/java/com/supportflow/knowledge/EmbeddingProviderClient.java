package com.supportflow.knowledge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configurable Embedding Provider Client for Knowledge Base & RAG chunk indexing.
 * Supports OpenAI-compatible /v1/embeddings REST endpoints when an external API key is configured,
 * and falls back transparently to SupportFlow's deterministic 12-dimension domain concept vector
 * in €0/month demo or offline CI environments.
 */
@Component
public class EmbeddingProviderClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingProviderClient.class);
    private static final Pattern FLOAT_PATTERN = Pattern.compile("-?\\d+\\.\\d+(?:[eE][-+]?\\d+)?|-?\\d+");

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

    public record EmbeddingResult(
            String vectorCsv,
            String provider,
            String model,
            boolean fallbackUsed
    ) {}

    private final String providerName;
    private final String baseUrl;
    private final String apiKey;
    private final String embeddingModel;
    private final int timeoutSeconds;
    private final HttpClient httpClient;

    public EmbeddingProviderClient(
            @Value("${app.ai.llm.provider:local-grounded}") String providerName,
            @Value("${app.ai.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.ai.llm.api-key:}") String apiKey,
            @Value("${app.ai.embedding.model:text-embedding-3-small}") String embeddingModel,
            @Value("${app.ai.llm.timeout-seconds:8}") int timeoutSeconds) {
        this.providerName = providerName;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(2, timeoutSeconds)))
                .build();
    }

    public boolean isExternalProviderConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"local-grounded".equalsIgnoreCase(providerName);
    }

    public EmbeddingResult embedWithFallback(String text) {
        String deterministicVector = computeDeterministicConceptVector(text);
        if (!isExternalProviderConfigured()) {
            return new EmbeddingResult(
                    deterministicVector,
                    "local-hybrid-lexical",
                    "supportflow-concept-12d",
                    true
            );
        }

        try {
            String escapedInput = (text == null ? "" : text)
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", " ")
                    .replace("\r", " ");
            String payload = """
                    {
                      "model": "%s",
                      "input": "%s",
                      "dimensions": 12
                    }
                    """.formatted(embeddingModel, escapedInput);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/embeddings"))
                    .timeout(Duration.ofSeconds(Math.max(2, timeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body() != null) {
                String parsedCsv = parseOpenAiEmbeddingArray(response.body());
                if (parsedCsv != null && !parsedCsv.isBlank()) {
                    return new EmbeddingResult(parsedCsv, providerName, embeddingModel, false);
                }
            }
            log.warn("External embedding endpoint returned status {}; using local 12D concept vector fallback.", response.statusCode());
        } catch (Exception ex) {
            log.warn("External embedding call failed ({}); using local 12D concept vector fallback.", ex.getMessage());
        }

        return new EmbeddingResult(
                deterministicVector,
                "local-hybrid-lexical-fallback",
                "supportflow-concept-12d",
                true
        );
    }

    public String computeDeterministicConceptVector(String text) {
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

    public static int defaultDimensionCount() {
        return SEMANTIC_DIMENSIONS.size();
    }

    private String parseOpenAiEmbeddingArray(String body) {
        int embIdx = body.indexOf("\"embedding\"");
        if (embIdx < 0) {
            return null;
        }
        int openBracket = body.indexOf('[', embIdx);
        int closeBracket = body.indexOf(']', openBracket + 1);
        if (openBracket < 0 || closeBracket < 0) {
            return null;
        }
        String arraySlice = body.substring(openBracket + 1, closeBracket);
        Matcher matcher = FLOAT_PATTERN.matcher(arraySlice);
        List<String> values = new ArrayList<>();
        while (matcher.find() && values.size() < 64) {
            values.add(matcher.group());
        }
        return values.isEmpty() ? null : String.join(",", values);
    }
}
