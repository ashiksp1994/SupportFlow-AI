package com.supportflow.ai;

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
@RequestMapping("/api/ai")
public class AiController {

    private final AiOrchestrationService aiService;

    public AiController(AiOrchestrationService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/tickets/{ticketId}/triage")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public AiOrchestrationService.AiTriageResult triageTicket(
            @PathVariable Long ticketId,
            Authentication auth) {
        String actor = auth != null ? auth.getName() : "AI_COPILOT";
        return aiService.triageTicket(ticketId, actor);
    }

    @PostMapping("/tickets/{ticketId}/suggest-reply")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public AiOrchestrationService.RagReplySuggestion suggestReply(
            @PathVariable Long ticketId,
            Authentication auth) {
        String actor = auth != null ? auth.getName() : "AI_COPILOT";
        return aiService.suggestReplyWithCitations(ticketId, actor);
    }

    @PostMapping("/tickets/{ticketId}/summarize")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public AiOrchestrationService.ConversationSummaryResult summarize(
            @PathVariable Long ticketId,
            Authentication auth) {
        String actor = auth != null ? auth.getName() : "AI_COPILOT";
        return aiService.summarizeConversation(ticketId, actor);
    }

    @PostMapping({"/tickets/{ticketId}/approvals", "/tickets/{ticketId}/propose-action"})
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public AiApprovalRequest proposeApproval(
            @PathVariable Long ticketId,
            @Valid @RequestBody AiOrchestrationService.ProposeToolCallRequest request,
            Authentication auth) {
        String actor = auth != null ? auth.getName() : "AI_COPILOT";
        return aiService.proposeToolAction(ticketId, request, actor);
    }

    @GetMapping("/approvals")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public List<AiApprovalRequest> listApprovals(@RequestParam(required = false) String status) {
        return aiService.listApprovals(status);
    }

    @PostMapping("/approvals/{approvalId}/decide")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public AiApprovalRequest decideApproval(
            @PathVariable Long approvalId,
            @Valid @RequestBody AiOrchestrationService.ApprovalDecisionRequest decision,
            Authentication auth) {
        String reviewer = auth != null ? auth.getName() : "SUPERVISOR";
        return aiService.decideApproval(approvalId, decision, reviewer);
    }
}
