package com.supportflow.ai;

import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Strict allowlist of consequential AI agent tools that require human-in-the-loop approval.
 */
public enum AllowedAiTool {

    ISSUE_SLA_SERVICE_CREDIT(
            "HIGH",
            Set.of("ISSUE_SLA_SERVICE_CREDIT", "ISSUE_SLA_CREDIT_REFUND", "REFUND_CREDIT", "REFUND_SUBSCRIPTION_CHARGE"),
            "Issue SLA service credit or billing adjustment to customer organisation"
    ),
    ESCALATE_TO_TIER3_ENGINEERING(
            "MEDIUM",
            Set.of("ESCALATE_TO_TIER3_ENGINEERING", "ESCALATE_TIER3"),
            "Escalate incident to Tier-3 Principal Engineering on-call queue"
    ),
    UPDATE_SLA_TIER(
            "HIGH",
            Set.of("UPDATE_SLA_TIER", "UPGRADE_SLA_TIER"),
            "Modify ticket SLA priority and resolution deadline"
    ),
    CLOSE_DUPLICATE(
            "LOW",
            Set.of("CLOSE_DUPLICATE", "MARK_DUPLICATE"),
            "Close ticket as verified duplicate of an existing incident"
    );

    private final String riskLevel;
    private final Set<String> acceptedAliases;
    private final String description;

    AllowedAiTool(String riskLevel, Set<String> acceptedAliases, String description) {
        this.riskLevel = riskLevel;
        this.acceptedAliases = acceptedAliases;
        this.description = description;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getDescription() {
        return description;
    }

    public static AllowedAiTool fromNameOrThrow(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI tool name is required");
        }
        String normalized = rawName.trim().toUpperCase(Locale.ROOT);
        for (AllowedAiTool tool : values()) {
            if (tool.name().equals(normalized) || tool.acceptedAliases.contains(normalized)) {
                return tool;
            }
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Rejected unapproved AI tool '" + rawName + "'. Allowed tools: ISSUE_SLA_SERVICE_CREDIT, ESCALATE_TO_TIER3_ENGINEERING, UPDATE_SLA_TIER, CLOSE_DUPLICATE");
    }

    public static String validateAndNormalizeParametersJson(String parametersJson, AllowedAiTool tool) {
        if (parametersJson == null || parametersJson.isBlank()) {
            return switch (tool) {
                case ISSUE_SLA_SERVICE_CREDIT -> "{\"creditPercent\":10,\"currency\":\"EUR\",\"maxCapEur\":250}";
                case ESCALATE_TO_TIER3_ENGINEERING -> "{\"targetQueue\":\"TIER3_PRINCIPAL_ENG\",\"notifyPagerDuty\":true}";
                case UPDATE_SLA_TIER -> "{\"newPriority\":\"CRITICAL\",\"slaHours\":2}";
                case CLOSE_DUPLICATE -> "{\"resolution\":\"DUPLICATE_INCIDENT\"}";
            };
        }
        String trimmed = parametersJson.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}") || trimmed.length() > 4000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tool parametersJson must be a valid JSON object string (max 4000 characters).");
        }
        return trimmed;
    }
}
