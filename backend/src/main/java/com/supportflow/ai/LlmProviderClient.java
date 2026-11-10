package com.supportflow.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configurable LLM Provider Client abstraction.
 * Supports OpenAI-compatible REST endpoints (OpenAI, Gemini OpenAI-compat, Groq, Ollama /v1)
 * with strict connect/read timeouts, and falls back to the deterministic grounded engine
 * when no external API key is configured (e.g., €0/month demo environment or offline CI tests).
 */
@Component
public class LlmProviderClient {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderClient.class);

    public record LlmCompletionResult(
            String content,
            String provider,
            String model,
            long latencyMs,
            boolean fallbackUsed
    ) {}

    private final String providerName;
    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final int timeoutSeconds;
    private final HttpClient httpClient;

    public LlmProviderClient(
            @Value("${app.ai.llm.provider:local-grounded}") String providerName,
            @Value("${app.ai.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.ai.llm.api-key:}") String apiKey,
            @Value("${app.ai.llm.model:gpt-4o-mini}") String modelName,
            @Value("${app.ai.llm.timeout-seconds:8}") int timeoutSeconds) {
        this.providerName = providerName;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(2, timeoutSeconds)))
                .build();
    }

    public boolean isExternalProviderConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"local-grounded".equalsIgnoreCase(providerName);
    }

    public LlmCompletionResult completeWithFallback(String systemPrompt, String userPrompt, String deterministicFallback) {
        long startNs = System.nanoTime();
        if (!isExternalProviderConfigured()) {
            long latencyMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            return new LlmCompletionResult(
                    deterministicFallback,
                    "local-grounded",
                    "supportflow-grounded-v2",
                    latencyMs,
                    true
            );
        }

        try {
            String escapedSystem = escapeJson(systemPrompt);
            String escapedUser = escapeJson(userPrompt);
            String payload = """
                    {
                      "model": "%s",
                      "temperature": 0.1,
                      "max_tokens": 500,
                      "messages": [
                        {"role": "system", "content": "%s"},
                        {"role": "user", "content": "%s"}
                      ]
                    }
                    """.formatted(escapeJson(modelName), escapedSystem, escapedUser);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(2, timeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latencyMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body() != null) {
                String extracted = extractContentFromOpenAiResponse(response.body());
                if (extracted != null && !extracted.isBlank()) {
                    return new LlmCompletionResult(extracted, providerName, modelName, latencyMs, false);
                }
            }
            log.warn("External LLM provider returned status {}; using deterministic grounded fallback.", response.statusCode());
        } catch (Exception ex) {
            log.warn("External LLM call failed ({}); using deterministic grounded fallback.", ex.getMessage());
        }

        long latencyMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
        return new LlmCompletionResult(
                deterministicFallback,
                "local-grounded-fallback",
                "supportflow-grounded-v2",
                latencyMs,
                true
        );
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String extractContentFromOpenAiResponse(String body) {
        int contentIdx = body.indexOf("\"content\"");
        if (contentIdx < 0) {
            return null;
        }
        int colonIdx = body.indexOf(':', contentIdx);
        int firstQuote = body.indexOf('"', colonIdx + 1);
        if (firstQuote < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = firstQuote + 1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escaped) {
                if (c == 'n') sb.append('\n');
                else if (c == '"') sb.append('"');
                else if (c == '\\') sb.append('\\');
                else sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
