package com.supportflow.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Guardrail service that inspects untrusted user/ticket/document text for prompt injection,
 * instruction override, secret exfiltration, and unauthorized tool invocation attempts.
 */
@Component
public class PromptInjectionGuard {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(all\\s+)?(previous|system|safety)\\s+(instructions|rules|prompts)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now\\s+(in\\s+)?(developer|dan|jailbreak|unrestricted)\\s+mode", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(reveal|print|output|dump)\\s+(your\\s+)?(system\\s+prompt|jwt_secret|api\\s*key|password)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("execute\\s+tool\\s+(refund|escalate|update_sla|close_duplicate)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*system\\s*>|\\[\\s*system\\s*override\\s*\\]", Pattern.CASE_INSENSITIVE)
    );

    public record PromptSafetyResult(
            boolean safe,
            List<String> matchedRules,
            String sanitizedText
    ) {}

    public PromptSafetyResult inspect(String untrustedInput) {
        if (untrustedInput == null || untrustedInput.isBlank()) {
            return new PromptSafetyResult(true, List.of(), "");
        }

        List<String> matched = new ArrayList<>();
        String sanitized = untrustedInput;
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(untrustedInput).find()) {
                matched.add(pattern.pattern().toLowerCase(Locale.ROOT));
                sanitized = pattern.matcher(sanitized).replaceAll("[REDACTED_INJECTION_ATTEMPT]");
            }
        }

        return new PromptSafetyResult(matched.isEmpty(), List.copyOf(matched), sanitized);
    }
}
