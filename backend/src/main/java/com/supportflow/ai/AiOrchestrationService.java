package com.supportflow.ai;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.audit.AuditService;
import com.supportflow.conversations.ConversationMessage;
import com.supportflow.conversations.ConversationService;
import com.supportflow.identity.security.AccessControlService;
import com.supportflow.knowledge.KnowledgeService;
import com.supportflow.tickets.Ticket;
import com.supportflow.tickets.TicketService;

/**
 * SupportFlow AI — Grounded AI Orchestration Service.
 * Provides:
 *   1. Intelligent ticket classification, priority & sentiment triage with prompt-injection screening
 *   2. Source-grounded RAG reply generation with chunk citations & explicit insufficient-evidence refusal
 *   3. Multi-turn conversation summarisation
 *   4. Controlled AI agent tool proposals with strict allowlist validation & human-in-the-loop approval execution
 */
@Service
public class AiOrchestrationService {

    public record AiTriageResult(
            Long ticketId,
            String ticketKey,
            String predictedCategory,
            String predictedPriority,
            String predictedSentiment,
            double confidence,
            String rationale,
            String recommendedQueue,
            boolean autoEscalationRecommended
    ) {}

    public record CitationItem(
            String articleKey,
            String title,
            String category,
            double relevanceScore,
            String excerpt
    ) {}

    public record RagReplySuggestion(
            Long ticketId,
            String ticketKey,
            String suggestedReply,
            double confidence,
            List<CitationItem> citations,
            boolean humanReviewRequired
    ) {}

    public record ConversationSummaryResult(
            Long ticketId,
            String ticketKey,
            String executiveSummary,
            List<String> keyPoints,
            String customerSentiment,
            String recommendedNextStep,
            int messageCount
    ) {}

    public record ProposeToolCallRequest(
            @NotBlank(message = "Tool name is required") String toolName,
            String parametersJson,
            String customReason
    ) {}

    public record ApprovalDecisionRequest(
            boolean approved,
            String decisionReason
    ) {}

    private final TicketService ticketService;
    private final ConversationService conversationService;
    private final KnowledgeService knowledgeService;
    private final AiApprovalRepository approvalRepository;
    private final AuditService auditService;
    private final AccessControlService accessControlService;
    private final PromptInjectionGuard promptInjectionGuard;
    private final LlmProviderClient llmProviderClient;

    public AiOrchestrationService(
            TicketService ticketService,
            ConversationService conversationService,
            KnowledgeService knowledgeService,
            AiApprovalRepository approvalRepository,
            AuditService auditService,
            AccessControlService accessControlService,
            PromptInjectionGuard promptInjectionGuard,
            LlmProviderClient llmProviderClient) {
        this.ticketService = ticketService;
        this.conversationService = conversationService;
        this.knowledgeService = knowledgeService;
        this.approvalRepository = approvalRepository;
        this.auditService = auditService;
        this.accessControlService = accessControlService;
        this.promptInjectionGuard = promptInjectionGuard;
        this.llmProviderClient = llmProviderClient;
    }

    @Transactional
    public AiTriageResult triageTicket(Long ticketId, String actor) {
        Ticket ticket = ticketService.findById(ticketId);
        String rawText = ticket.getSubject() + " " + ticket.getDescription();
        PromptInjectionGuard.PromptSafetyResult safety = promptInjectionGuard.inspect(rawText);
        if (!safety.safe()) {
            auditService.recordEvent(
                    "AI_PROMPT_INJECTION_DETECTED",
                    actor,
                    "TICKET",
                    ticket.getTicketKey(),
                    "Detected prompt-injection pattern(s) during AI triage: " + String.join(", ", safety.matchedRules()));
        }

        String text = safety.sanitizedText().toLowerCase(Locale.ROOT);

        String category = "GENERAL";
        if (containsAny(text, "sso", "saml", "oidc", "login", "mfa", "password", "auth", "token", "certificate", "401", "permission")) {
            category = "SECURITY";
        } else if (containsAny(text, "invoice", "billing", "refund", "charge", "subscription", "credit", "payment", "stripe")) {
            category = "BILLING";
        } else if (containsAny(text, "api", "webhook", "429", "500", "timeout", "sdk", "endpoint", "latency", "outage", "database")) {
            category = "TECHNICAL_API";
        } else if (containsAny(text, "sla", "contract", "enterprise", "onboarding", "organisation")) {
            category = "ENTERPRISE_OPS";
        }

        String priority = "MEDIUM";
        boolean recommendEscalation = false;
        if (containsAny(text, "outage", "production down", "all users", "critical", "breach", "blocked", "p1", "urgent", "security")) {
            priority = "CRITICAL";
            recommendEscalation = true;
        } else if (containsAny(text, "error", "failing", "500", "401", "429", "vip", "high", "unable")) {
            priority = "HIGH";
        } else if (containsAny(text, "question", "how to", "documentation", "feature request")) {
            priority = "LOW";
        }

        String sentiment = "NEUTRAL";
        if (containsAny(text, "urgent", "unacceptable", "blocked", "outage", "frustrated", "immediately", "broken", "breach")) {
            sentiment = "FRUSTRATED_URGENT";
        } else if (containsAny(text, "thanks", "appreciate", "great", "helpful")) {
            sentiment = "POSITIVE";
        }

        String queue = switch (category) {
            case "SECURITY" -> "Identity & Security Engineering";
            case "BILLING" -> "Revenue & Billing Operations";
            case "TECHNICAL_API" -> "Platform SRE & API Tier-2";
            case "ENTERPRISE_OPS" -> "Enterprise Customer Success";
            default -> "General Support Tier-1";
        };

        double confidence = safety.safe() ? (recommendEscalation ? 0.94 : 0.89) : 0.65;
        String rationale = String.format(
                Locale.ROOT,
                "Classified as %s (%s priority, sentiment=%s) routed to %s.%s",
                category,
                priority,
                sentiment,
                queue,
                safety.safe() ? "" : " [WARN: Sanitized untrusted prompt-injection sequence]");

        ticket.setCategory(category);
        ticket.setPriority(priority);
        ticket.setSentiment(sentiment);
        ticket.setAiTriaged(true);
        ticket.setAiConfidence(confidence);
        ticketService.saveTicket(ticket);

        auditService.recordEvent("AI_TICKET_TRIAGED", actor, "TICKET", ticket.getTicketKey(),
                "AI classified ticket: category=" + category + ", priority=" + priority + ", confidence=" + confidence);

        return new AiTriageResult(
                ticket.getId(),
                ticket.getTicketKey(),
                category,
                priority,
                sentiment,
                confidence,
                rationale,
                queue,
                recommendEscalation
        );
    }

    @Transactional
    public RagReplySuggestion suggestReplyWithCitations(Long ticketId, String actor) {
        Ticket ticket = ticketService.findById(ticketId);
        List<ConversationMessage> messages = conversationService.getMessagesForTicket(ticketId);

        StringBuilder queryBuilder = new StringBuilder(ticket.getSubject())
                .append(" ")
                .append(ticket.getDescription());
        if (!messages.isEmpty()) {
            queryBuilder.append(" ").append(messages.getLast().getContent());
        }

        PromptInjectionGuard.PromptSafetyResult safety = promptInjectionGuard.inspect(queryBuilder.toString());
        if (!safety.safe()) {
            auditService.recordEvent(
                    "AI_PROMPT_INJECTION_DETECTED",
                    actor,
                    "TICKET",
                    ticket.getTicketKey(),
                    "Sanitized prompt-injection pattern(s) before RAG retrieval: " + String.join(", ", safety.matchedRules()));
        }

        List<KnowledgeService.KnowledgeSearchResult> retrieved = knowledgeService.search(safety.sanitizedText(), 3);
        List<CitationItem> citations = retrieved.stream()
                .map(r -> new CitationItem(r.articleKey(), r.title(), r.category(), r.relevanceScore(), r.snippet()))
                .toList();

        String customerDisplay = ticket.getCustomerName() != null ? ticket.getCustomerName() : "there";

        // Strict Grounded RAG Guardrail: NEVER fabricate citations or invent facts when KB evidence is insufficient.
        if (citations.isEmpty()) {
            String insufficientEvidenceReply = "Insufficient Knowledge Base Evidence: No verified knowledge base articles met the minimum relevance threshold for ticket "
                    + ticket.getTicketKey() + " (\"" + ticket.getSubject() + "\").\n\n"
                    + "Recommended Action:\n"
                    + "1. Do not send an unverified AI reply to " + customerDisplay + ".\n"
                    + "2. Escalate to the appropriate specialist queue or publish a verified KB runbook before responding.";

            auditService.recordEvent("AI_RAG_INSUFFICIENT_EVIDENCE", actor, "TICKET", ticket.getTicketKey(),
                    "RAG retrieval returned 0 articles above relevance threshold; refused to fabricate citations.");

            return new RagReplySuggestion(
                    ticket.getId(),
                    ticket.getTicketKey(),
                    insufficientEvidenceReply,
                    0.20,
                    List.of(),
                    true
            );
        }

        StringBuilder deterministicReply = new StringBuilder();
        deterministicReply.append("Hi ").append(customerDisplay).append(",\n\n");
        deterministicReply.append("Thank you for contacting SupportFlow Enterprise Support regarding **")
                .append(ticket.getSubject()).append("** (Ref: `").append(ticket.getTicketKey()).append("`).\n\n");
        deterministicReply.append("Based on our verified engineering knowledge base:\n");
        for (CitationItem c : citations) {
            deterministicReply.append("- **[").append(c.articleKey()).append("] ").append(c.title()).append("**: ")
                    .append(c.excerpt()).append("\n");
        }
        deterministicReply.append("\nPlease let us know once you have applied the steps in `[")
                .append(citations.getFirst().articleKey())
                .append("]` or if you would like us to join a live troubleshooting session.\n\n")
                .append("Best regards,\nSupportFlow Technical Support");

        LlmProviderClient.LlmCompletionResult llmResult = llmProviderClient.completeWithFallback(
                "You are a grounded enterprise support copilot. Answer ONLY using the provided KB citations and cite article keys in brackets like [KB-101]. Never invent facts.",
                "Ticket: " + safety.sanitizedText() + "\nCitations: " + citations,
                deterministicReply.toString()
        );

        double confidence = citations.getFirst().relevanceScore();
        auditService.recordEvent("AI_RAG_REPLY_GENERATED", actor, "TICKET", ticket.getTicketKey(),
                "Generated RAG reply with " + citations.size() + " citation(s) via " + llmResult.provider());

        return new RagReplySuggestion(
                ticket.getId(),
                ticket.getTicketKey(),
                llmResult.content(),
                confidence,
                citations,
                true
        );
    }

    @Transactional
    public ConversationSummaryResult summarizeConversation(Long ticketId, String actor) {
        Ticket ticket = ticketService.findById(ticketId);
        List<ConversationMessage> messages = conversationService.getMessagesForTicket(ticketId);

        List<String> keyPoints = new ArrayList<>();
        keyPoints.add("Incident reported on ticket " + ticket.getTicketKey() + ": " + ticket.getSubject());
        keyPoints.add("Current lifecycle state: status=" + ticket.getStatus() + ", priority=" + ticket.getPriority() + ", category=" + ticket.getCategory());
        for (ConversationMessage m : messages) {
            String snippet = m.getContent().length() > 120 ? m.getContent().substring(0, 117) + "..." : m.getContent();
            keyPoints.add(m.getSenderType() + " (" + m.getSenderName() + "): " + snippet);
        }

        String nextStep = "HIGH".equals(ticket.getPriority()) || "CRITICAL".equals(ticket.getPriority())
                ? "Verify customer remediation using RAG-cited runbook or approve Tier-3 engineering escalation."
                : "Send grounded response suggestion and monitor customer confirmation within SLA window.";

        String execSummary = String.format(
                Locale.ROOT,
                "%s (%s) reported '%s' [%s/%s]. %d conversation message(s) exchanged. Assigned agent: %s.",
                ticket.getCustomerName() != null ? ticket.getCustomerName() : "Customer",
                ticket.getOrganisationName() != null ? ticket.getOrganisationName() : "Standard Tenant",
                ticket.getSubject(),
                ticket.getCategory(),
                ticket.getPriority(),
                messages.size(),
                ticket.getAssignedAgentUsername() != null ? ticket.getAssignedAgentUsername() : "Unassigned"
        );

        ticket.setAiSummary(execSummary);
        ticketService.saveTicket(ticket);

        auditService.recordEvent("AI_CONVERSATION_SUMMARIZED", actor, "TICKET", ticket.getTicketKey(),
                "Generated executive conversation summary (" + messages.size() + " messages)");

        return new ConversationSummaryResult(
                ticket.getId(),
                ticket.getTicketKey(),
                execSummary,
                keyPoints,
                ticket.getSentiment(),
                nextStep,
                messages.size()
        );
    }

    @Transactional
    public AiApprovalRequest proposeToolAction(Long ticketId, ProposeToolCallRequest request, String actor) {
        accessControlService.requireStaff();
        Ticket ticket = ticketService.findById(ticketId);

        PromptInjectionGuard.PromptSafetyResult ticketSafety =
                promptInjectionGuard.inspect(ticket.getSubject() + " " + ticket.getDescription() + " " + request.customReason());
        if (!ticketSafety.safe()) {
            auditService.recordEvent(
                    "AI_TOOL_PROPOSAL_BLOCKED_INJECTION",
                    actor,
                    "TICKET",
                    ticket.getTicketKey(),
                    "Blocked AI tool proposal due to prompt-injection signature: " + String.join(", ", ticketSafety.matchedRules()));
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "AI tool proposal blocked by PromptInjectionGuard: untrusted instruction override detected.");
        }

        AllowedAiTool allowedTool = AllowedAiTool.fromNameOrThrow(request.toolName());
        String normalizedParams = AllowedAiTool.validateAndNormalizeParametersJson(request.parametersJson(), allowedTool);

        String summary = switch (allowedTool) {
            case ISSUE_SLA_SERVICE_CREDIT ->
                    "Issue 10% SLA service credit to organisation " + (ticket.getOrganisationName() != null ? ticket.getOrganisationName() : "Customer");
            case ESCALATE_TO_TIER3_ENGINEERING ->
                    "Escalate ticket " + ticket.getTicketKey() + " to Tier-3 Principal Engineering & shorten SLA to 2h";
            case UPDATE_SLA_TIER ->
                    "Override ticket " + ticket.getTicketKey() + " priority to CRITICAL with 2-hour SLA target";
            case CLOSE_DUPLICATE ->
                    "Close ticket " + ticket.getTicketKey() + " as verified duplicate incident";
        };

        AiApprovalRequest approval = new AiApprovalRequest();
        approval.setTicketId(ticket.getId());
        approval.setTicketKey(ticket.getTicketKey());
        approval.setToolName(allowedTool.name());
        approval.setActionSummary(summary);
        approval.setParametersJson(normalizedParams);
        approval.setAiReasoning(request.customReason() != null && !request.customReason().isBlank()
                ? request.customReason().trim()
                : "AI Agent evaluated ticket priority (" + ticket.getPriority() + ") and SLA risk [Risk=" + allowedTool.getRiskLevel() + "]; human approval required before consequential state change.");
        approval.setConfidence(0.91);
        approval.setStatus("PENDING");
        approval.setRequestedBy(actor);

        AiApprovalRequest saved = approvalRepository.save(approval);
        auditService.recordEvent("AI_TOOL_APPROVAL_REQUESTED", actor, "AI_APPROVAL", String.valueOf(saved.getId()),
                "Proposed controlled tool '" + allowedTool.name() + "' on ticket " + ticket.getTicketKey());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AiApprovalRequest> listApprovals(String status) {
        accessControlService.requireStaff();
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            return approvalRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(Locale.ROOT));
        }
        return approvalRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public AiApprovalRequest decideApproval(Long approvalId, ApprovalDecisionRequest decision, String reviewer) {
        accessControlService.requireStaff();
        AiApprovalRequest approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI approval request not found: " + approvalId));

        if (!"PENDING".equals(approval.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval request has already been decided: " + approval.getStatus());
        }

        Ticket ticket = ticketService.findById(approval.getTicketId());
        approval.setDecidedBy(reviewer);
        approval.setDecidedAt(Instant.now());
        approval.setDecisionReason(decision.decisionReason() != null && !decision.decisionReason().isBlank()
                ? decision.decisionReason().trim()
                : (decision.approved() ? "Approved by human supervisor" : "Rejected by human supervisor"));

        if (!decision.approved()) {
            approval.setStatus("REJECTED");
            approval.setExecutionResult("Action rejected by " + reviewer + ". No state mutation was executed.");
            AiApprovalRequest saved = approvalRepository.save(approval);
            auditService.recordEvent("AI_TOOL_APPROVAL_REJECTED", reviewer, "AI_APPROVAL", String.valueOf(saved.getId()),
                    "Rejected tool '" + saved.getToolName() + "' on ticket " + saved.getTicketKey());
            return saved;
        }

        approval.setStatus("APPROVED");
        AllowedAiTool tool = AllowedAiTool.fromNameOrThrow(approval.getToolName());
        String resultMessage = executeApprovedTool(tool, ticket, reviewer);
        approval.setExecutionResult(resultMessage);
        AiApprovalRequest saved = approvalRepository.save(approval);

        auditService.recordEvent("AI_TOOL_APPROVAL_EXECUTED", reviewer, "AI_APPROVAL", String.valueOf(saved.getId()),
                "Approved and executed tool '" + saved.getToolName() + "' on ticket " + saved.getTicketKey() + ": " + resultMessage);
        return saved;
    }

    private String executeApprovedTool(AllowedAiTool tool, Ticket ticket, String reviewer) {
        return switch (tool) {
            case ESCALATE_TO_TIER3_ENGINEERING -> {
                ticketService.escalateTicket(ticket.getId(), "AI-assisted Tier-3 escalation approved by " + reviewer, reviewer);
                yield "Escalated ticket " + ticket.getTicketKey() + " to Tier-3 Principal Engineering and notified on-call channel.";
            }
            case ISSUE_SLA_SERVICE_CREDIT -> {
                conversationService.addMessage(
                        ticket.getId(),
                        new ConversationService.AddMessageRequest(
                                "[AI TOOL EXECUTED — APPROVED BY " + reviewer + "] Applied 10% SLA service credit to customer account ledger.",
                                "SYSTEM",
                                "SupportFlow AI Agent",
                                true,
                                true,
                                null),
                        reviewer);
                yield "Issued 10% SLA service credit and recorded internal audit note on " + ticket.getTicketKey() + ".";
            }
            case UPDATE_SLA_TIER -> {
                ticket.setPriority("CRITICAL");
                ticket.setSlaDueAt(Instant.now().plus(2, ChronoUnit.HOURS));
                ticketService.saveTicket(ticket);
                yield "Updated ticket " + ticket.getTicketKey() + " priority to CRITICAL with 2-hour SLA deadline.";
            }
            case CLOSE_DUPLICATE -> {
                ticket.setStatus("CLOSED");
                ticketService.saveTicket(ticket);
                yield "Closed ticket " + ticket.getTicketKey() + " as verified duplicate incident.";
            }
        };
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
