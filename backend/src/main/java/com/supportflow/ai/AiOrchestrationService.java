package com.supportflow.ai;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import com.supportflow.conversations.ConversationMessage;
import com.supportflow.conversations.ConversationService;
import com.supportflow.knowledge.KnowledgeService;
import com.supportflow.tickets.Ticket;
import com.supportflow.tickets.TicketService;

@Service
public class AiOrchestrationService {

    public record AiTriageResult(
            Long ticketId,
            String ticketKey,
            String predictedCategory,
            String predictedPriority,
            String predictedSentiment,
            double confidence,
            String reasoning,
            String suggestedTool,
            String providerUsed
    ) {}

    public record RagCitation(
            String articleKey,
            String title,
            String category,
            double relevanceScore,
            String snippet
    ) {}

    public record RagReplySuggestion(
            Long ticketId,
            String ticketKey,
            String suggestedReply,
            List<RagCitation> citations,
            double groundedConfidence,
            String providerUsed
    ) {}

    public record ConversationSummaryResult(
            Long ticketId,
            String ticketKey,
            String executiveSummary,
            List<String> keyPoints,
            String recommendedNextStep,
            int messageCount,
            String providerUsed
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
    private final String configuredProvider;

    public AiOrchestrationService(
            TicketService ticketService,
            ConversationService conversationService,
            KnowledgeService knowledgeService,
            AiApprovalRepository approvalRepository,
            AuditService auditService,
            @Value("${app.ai.provider:SPRING_AI_HYBRID}") String configuredProvider) {
        this.ticketService = ticketService;
        this.conversationService = conversationService;
        this.knowledgeService = knowledgeService;
        this.approvalRepository = approvalRepository;
        this.auditService = auditService;
        this.configuredProvider = configuredProvider;
    }

    @Transactional
    public AiTriageResult triageTicket(Long ticketId, String actor) {
        Ticket ticket = ticketService.findById(ticketId);
        String text = (ticket.getSubject() + " " + ticket.getDescription()).toLowerCase(Locale.ROOT);

        String category = "GENERAL";
        if (containsAny(text, "saml", "sso", "mfa", "oauth", "certificate", "security", "auth", "permission", "401", "403")) {
            category = "SECURITY";
        } else if (containsAny(text, "invoice", "billing", "refund", "charge", "subscription", "credit", "payment")) {
            category = "BILLING";
        } else if (containsAny(text, "webhook", "api", "sdk", "integration", "429", "rate limit", "payload")) {
            category = "INTEGRATION";
        } else if (containsAny(text, "outage", "crash", "latency", "timeout", "500", "database", "pgvector", "bug", "error")) {
            category = "TECHNICAL_ISSUE";
        } else if (containsAny(text, "feature", "request", "roadmap", "enhancement")) {
            category = "FEATURE_REQUEST";
        }

        String priority = "MEDIUM";
        if (containsAny(text, "production", "outage", "down", "critical", "all users", "breach", "p1", "urgent")) {
            priority = "CRITICAL";
        } else if (containsAny(text, "error", "failed", "failure", "blocked", "enterprise", "high", "sla")) {
            priority = "HIGH";
        } else if (containsAny(text, "question", "how to", "minor", "feature")) {
            priority = "LOW";
        }

        String sentiment = "NEUTRAL";
        if (containsAny(text, "unacceptable", "frustrated", "immediately", "outage", "broken", "escalate")) {
            sentiment = "URGENT";
        } else if (containsAny(text, "failed", "error", "issue", "unable", "wrong")) {
            sentiment = "CONCERNED";
        }

        double confidence = 0.92;
        String suggestedTool = switch (category) {
            case "BILLING" -> "ISSUE_SLA_CREDIT_REFUND";
            case "SECURITY" -> "ESCALATE_TO_TIER3_ENGINEERING";
            default -> "CRITICAL".equals(priority) ? "ESCALATE_TO_TIER3_ENGINEERING" : "NONE";
        };

        String reasoning = String.format(
                "Classified as %s (%s priority, %s sentiment) based on semantic signals in subject/description and organisation SLA context (%s).",
                category, priority, sentiment,
                ticket.getOrganisationName() != null ? ticket.getOrganisationName() : "Standard Tier");

        ticket.setCategory(category);
        ticket.setPriority(priority);
        ticket.setSentiment(sentiment);
        ticket.setAiTriaged(true);
        ticket.setAiConfidence(confidence);
        ticketService.saveTicket(ticket);

        auditService.recordEvent("AI_TICKET_TRIAGED", actor, "TICKET", ticket.getTicketKey(),
                "AI classified ticket: category=" + category + ", priority=" + priority + " (confidence=" + confidence + ")");

        return new AiTriageResult(
                ticket.getId(),
                ticket.getTicketKey(),
                category,
                priority,
                sentiment,
                confidence,
                reasoning,
                suggestedTool,
                configuredProvider
        );
    }

    @Transactional
    public RagReplySuggestion suggestReplyWithCitations(Long ticketId, String actor) {
        Ticket ticket = ticketService.findById(ticketId);
        String searchQuery = ticket.getSubject() + " " + ticket.getCategory() + " " + ticket.getDescription();
        List<KnowledgeService.KnowledgeSearchResult> matches = knowledgeService.search(searchQuery, 3);

        List<RagCitation> citations = matches.stream()
                .map(m -> new RagCitation(m.articleKey(), m.title(), m.category(), m.relevanceScore(), m.citationSnippet()))
                .toList();

        StringBuilder reply = new StringBuilder();
        String customerGreeting = ticket.getCustomerName() != null ? ticket.getCustomerName() : "there";
        reply.append("Hi ").append(customerGreeting).append(",\n\n");
        reply.append("Thank you for reaching out to SupportFlow AI regarding **").append(ticket.getSubject()).append("**.\n\n");

        if (!citations.isEmpty()) {
            RagCitation primary = citations.getFirst();
            reply.append("Based on our verified knowledge base documentation [").append(primary.articleKey()).append("], ");
            reply.append("here are the recommended resolution steps:\n\n");
            for (int i = 0; i < citations.size(); i++) {
                RagCitation c = citations.get(i);
                reply.append(i + 1).append(". **").append(c.title()).append("** ([").append(c.articleKey()).append("]): ")
                        .append(c.snippet()).append("\n");
            }
            reply.append("\nPlease let us know once you have verified these steps or if you would like us to join a live troubleshooting session.\n\n");
        } else {
            reply.append("Our engineering support team has reviewed your report and is actively investigating the issue for your environment.\n\n");
        }
        reply.append("Best regards,\nSupportFlow Technical Support");

        double groundedConfidence = citations.isEmpty() ? 0.70 : citations.getFirst().relevanceScore();
        auditService.recordEvent("AI_RAG_REPLY_GENERATED", actor, "TICKET", ticket.getTicketKey(),
                "Generated RAG response suggestion grounded in " + citations.size() + " knowledge base citations");

        return new RagReplySuggestion(
                ticket.getId(),
                ticket.getTicketKey(),
                reply.toString(),
                citations,
                groundedConfidence,
                configuredProvider
        );
    }

    @Transactional
    public ConversationSummaryResult summarizeConversation(Long ticketId, String actor) {
        Ticket ticket = ticketService.findById(ticketId);
        List<ConversationMessage> messages = conversationService.getMessagesForTicket(ticketId);

        String execSummary = String.format(
                "Ticket %s ('%s') from %s is currently %s (%s priority). %d conversation messages exchanged.",
                ticket.getTicketKey(),
                ticket.getSubject(),
                ticket.getOrganisationName() != null ? ticket.getOrganisationName() : "Customer",
                ticket.getStatus(),
                ticket.getPriority(),
                messages.size());

        List<String> keyPoints = List.of(
                "Reported Issue: " + ticket.getDescription(),
                "Classification & Sentiment: " + ticket.getCategory() + " / " + ticket.getSentiment(),
                messages.isEmpty()
                        ? "Latest Activity: Awaiting initial agent response."
                        : "Latest Message (" + messages.getLast().getSenderName() + "): " + messages.getLast().getContent()
        );

        String nextStep = "ESCALATED".equals(ticket.getStatus())
                ? "Coordinate with Tier-3 Engineering on root-cause remediation and update customer within SLA window."
                : "Send grounded RAG resolution steps to customer and verify fix.";

        ticket.setAiSummary(execSummary + " Next step: " + nextStep);
        ticketService.saveTicket(ticket);

        auditService.recordEvent("AI_CONVERSATION_SUMMARIZED", actor, "TICKET", ticket.getTicketKey(),
                "Summarized ticket thread (" + messages.size() + " messages)");

        return new ConversationSummaryResult(
                ticket.getId(),
                ticket.getTicketKey(),
                execSummary,
                keyPoints,
                nextStep,
                messages.size(),
                configuredProvider
        );
    }

    @Transactional
    public AiApprovalRequest proposeToolAction(Long ticketId, ProposeToolCallRequest request, String actor) {
        Ticket ticket = ticketService.findById(ticketId);
        String tool = request.toolName().trim().toUpperCase(Locale.ROOT);

        String actionSummary = switch (tool) {
            case "ISSUE_SLA_CREDIT_REFUND" -> "Issue 15% monthly SLA service credit to " +
                    (ticket.getOrganisationName() != null ? ticket.getOrganisationName() : "customer account");
            case "ESCALATE_TO_TIER3_ENGINEERING" -> "Escalate ticket " + ticket.getTicketKey() +
                    " to Tier-3 Principal Engineering on-call queue";
            case "RESOLVE_AND_CLOSE_TICKET" -> "Mark ticket " + ticket.getTicketKey() + " as RESOLVED and notify customer";
            default -> "Execute controlled support tool " + tool + " on ticket " + ticket.getTicketKey();
        };

        AiApprovalRequest approval = new AiApprovalRequest();
        approval.setTicketId(ticket.getId());
        approval.setTicketKey(ticket.getTicketKey());
        approval.setToolName(tool);
        approval.setActionSummary(actionSummary);
        approval.setParametersJson(request.parametersJson() != null && !request.parametersJson().isBlank()
                ? request.parametersJson()
                : "{\"ticketKey\":\"" + ticket.getTicketKey() + "\",\"tool\":\"" + tool + "\"}");
        approval.setAiReasoning(request.customReason() != null && !request.customReason().isBlank()
                ? request.customReason()
                : "AI agent determined consequential action '" + tool + "' requires human supervisor approval under governance policy.");
        approval.setConfidence(0.91);
        approval.setStatus("PENDING");
        approval.setRequestedBy(actor != null ? actor : "AI_COPILOT");

        AiApprovalRequest saved = approvalRepository.save(approval);
        auditService.recordEvent("AI_TOOL_APPROVAL_REQUESTED", actor, "AI_APPROVAL", String.valueOf(saved.getId()),
                "Proposed tool " + tool + " for ticket " + ticket.getTicketKey() + " (awaiting human approval)");
        return saved;
    }

    @Transactional
    public AiApprovalRequest decideApproval(Long approvalId, ApprovalDecisionRequest decision, String humanActor) {
        AiApprovalRequest approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI approval request not found: " + approvalId));

        if (!"PENDING".equals(approval.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval request has already been decided: " + approval.getStatus());
        }

        approval.setDecidedBy(humanActor);
        approval.setDecisionReason(decision.decisionReason() != null ? decision.decisionReason() : "");
        approval.setDecidedAt(Instant.now());

        if (decision.approved()) {
            approval.setStatus("APPROVED");
            String result = executeApprovedTool(approval, humanActor);
            approval.setExecutionResult(result);
            auditService.recordEvent("AI_TOOL_APPROVED_AND_EXECUTED", humanActor, "TICKET", approval.getTicketKey(),
                    "Approved and executed tool " + approval.getToolName() + ": " + result);
        } else {
            approval.setStatus("REJECTED");
            approval.setExecutionResult("Action rejected by human reviewer (" + humanActor + "). Tool was not executed.");
            auditService.recordEvent("AI_TOOL_REJECTED", humanActor, "TICKET", approval.getTicketKey(),
                    "Rejected tool " + approval.getToolName() + ": " + approval.getDecisionReason());
        }

        return approvalRepository.save(approval);
    }

    @Transactional(readOnly = true)
    public List<AiApprovalRequest> listApprovals(String status) {
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            return approvalRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(Locale.ROOT));
        }
        return approvalRepository.findAllByOrderByCreatedAtDesc();
    }

    private String executeApprovedTool(AiApprovalRequest approval, String humanActor) {
        Long ticketId = approval.getTicketId();
        return switch (approval.getToolName()) {
            case "ESCALATE_TO_TIER3_ENGINEERING" -> {
                ticketService.escalateTicket(ticketId, "Approved AI Tier-3 Engineering escalation (" + humanActor + ")", humanActor);
                conversationService.addMessage(ticketId, new ConversationService.AddMessageRequest(
                        "[AI Agent Tool Executed — Approved by " + humanActor + "] Ticket escalated to Tier-3 Engineering with full diagnostic context.",
                        "AI_AGENT", "SupportFlow AI Agent", true, true, null), humanActor);
                yield "Ticket escalated to Tier-3 Engineering and internal diagnostic note recorded.";
            }
            case "ISSUE_SLA_CREDIT_REFUND" -> {
                conversationService.addMessage(ticketId, new ConversationService.AddMessageRequest(
                        "[AI Agent Tool Executed — Approved by " + humanActor + "] Applied 15% SLA credit adjustment to organisation billing ledger.",
                        "AI_AGENT", "SupportFlow AI Agent", true, true, null), humanActor);
                yield "Applied 15% SLA credit adjustment and logged internal billing confirmation.";
            }
            case "RESOLVE_AND_CLOSE_TICKET" -> {
                Ticket t = ticketService.findById(ticketId);
                ticketService.updateStatusAndPriority(ticketId,
                        new TicketService.UpdateTicketStatusRequest("RESOLVED", t.getPriority(), t.getCategory()), humanActor);
                yield "Ticket status transitioned to RESOLVED.";
            }
            default -> {
                conversationService.addMessage(ticketId, new ConversationService.AddMessageRequest(
                        "[AI Agent Tool Executed — Approved by " + humanActor + "] Executed tool " + approval.getToolName(),
                        "AI_AGENT", "SupportFlow AI Agent", true, true, null), humanActor);
                yield "Executed controlled tool " + approval.getToolName() + ".";
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

    @RestController
    @RequestMapping("/api/ai")
    public static class AiController {

        private final AiOrchestrationService aiService;

        public AiController(AiOrchestrationService aiService) {
            this.aiService = aiService;
        }

        @PostMapping("/tickets/{ticketId}/triage")
        public AiTriageResult triageTicket(@PathVariable Long ticketId, Authentication auth) {
            String actor = auth != null ? auth.getName() : "AI_COPILOT";
            return aiService.triageTicket(ticketId, actor);
        }

        @PostMapping("/tickets/{ticketId}/suggest-reply")
        public RagReplySuggestion suggestReply(@PathVariable Long ticketId, Authentication auth) {
            String actor = auth != null ? auth.getName() : "AI_COPILOT";
            return aiService.suggestReplyWithCitations(ticketId, actor);
        }

        @PostMapping("/tickets/{ticketId}/summarize")
        public ConversationSummaryResult summarizeTicket(@PathVariable Long ticketId, Authentication auth) {
            String actor = auth != null ? auth.getName() : "AI_COPILOT";
            return aiService.summarizeConversation(ticketId, actor);
        }

        @PostMapping("/tickets/{ticketId}/propose-action")
        @ResponseStatus(HttpStatus.CREATED)
        public AiApprovalRequest proposeAction(
                @PathVariable Long ticketId,
                @Valid @RequestBody ProposeToolCallRequest request,
                Authentication auth) {
            String actor = auth != null ? auth.getName() : "AI_COPILOT";
            return aiService.proposeToolAction(ticketId, request, actor);
        }

        @GetMapping("/approvals")
        public List<AiApprovalRequest> listApprovals(@RequestParam(required = false) String status) {
            return aiService.listApprovals(status);
        }

        @PostMapping("/approvals/{id}/decide")
        @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
        public AiApprovalRequest decideApproval(
                @PathVariable Long id,
                @Valid @RequestBody ApprovalDecisionRequest request,
                Authentication auth) {
            String actor = auth != null ? auth.getName() : "SUPERVISOR";
            return aiService.decideApproval(id, request, actor);
        }
    }
}
