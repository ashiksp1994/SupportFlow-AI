package com.supportflow.ai;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.supportflow.SupportFlowApplication;
import com.supportflow.identity.security.AccessControlService;
import com.supportflow.knowledge.KnowledgeArticle;
import com.supportflow.knowledge.KnowledgeService;
import com.supportflow.tickets.Ticket;
import com.supportflow.tickets.TicketService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = SupportFlowApplication.class)
@ActiveProfiles("test")
class AiWorkflowsIntegrationTest {

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private AiOrchestrationService aiService;

    @Autowired
    private AccessControlService accessControlService;

    @Test
    void shouldPerformRagSearchTriageReplyWithCitationsSummarisationAndHumanApprovedToolExecution() {
        accessControlService.runAsSystem(() -> {
            KnowledgeArticle kb = knowledgeService.createArticle(
                    new KnowledgeService.CreateArticleRequest(
                            "Resolving SAML 2.0 Signature Validation & Certificate Rotation Errors",
                            "SECURITY",
                            "saml,sso,certificate,idp,401",
                            "When rotating your Identity Provider (Okta / Entra ID) signing certificate, upload the new X.509 PEM certificate under Organisation Settings -> SSO -> Secondary Certificate before switching the active IdP key."),
                    "admin");
            assertNotNull(kb.getArticleKey());

            List<KnowledgeService.KnowledgeSearchResult> searchResults = knowledgeService.search("SAML certificate validation error", 3);
            assertFalse(searchResults.isEmpty());
            assertEquals(kb.getArticleKey(), searchResults.getFirst().articleKey());
            assertNotNull(searchResults.getFirst().embeddingProvider());
            assertTrue(searchResults.getFirst().fallbackUsed());

            Ticket ticket = ticketService.createTicket(
                    new TicketService.CreateTicketRequest(
                            "Urgent production SAML SSO certificate error blocking login",
                            "All users in our production tenant are blocked by SAML signature 401 errors after IdP rotation.",
                            "MEDIUM",
                            "GENERAL",
                            null,
                            "sarah.chen"),
                    "customer");

            // 1. AI Triage with fallback transparency metadata
            AiOrchestrationService.AiTriageResult triage = aiService.triageTicket(ticket.getId(), "AI_COPILOT");
            assertEquals("SECURITY", triage.predictedCategory());
            assertEquals("CRITICAL", triage.predictedPriority());
            assertNotNull(triage.providerUsed());
            assertNotNull(triage.modelUsed());
            assertTrue(triage.fallbackUsed());

            // 2. RAG Reply Suggestion with Citations & fallback transparency metadata
            AiOrchestrationService.RagReplySuggestion ragReply = aiService.suggestReplyWithCitations(ticket.getId(), "AI_COPILOT");
            assertFalse(ragReply.citations().isEmpty());
            assertTrue(ragReply.suggestedReply().contains(kb.getArticleKey()));
            assertNotNull(ragReply.providerUsed());
            assertTrue(ragReply.fallbackUsed());

            // 3. Conversation Summarisation with fallback transparency metadata
            AiOrchestrationService.ConversationSummaryResult summary = aiService.summarizeConversation(ticket.getId(), "AI_COPILOT");
            assertNotNull(summary.executiveSummary());
            assertFalse(summary.keyPoints().isEmpty());
            assertNotNull(summary.providerUsed());
            assertTrue(summary.fallbackUsed());

            // 4. Controlled Tool Calling + Human-in-the-Loop Approval
            AiApprovalRequest pending = aiService.proposeToolAction(
                    ticket.getId(),
                    new AiOrchestrationService.ProposeToolCallRequest("ESCALATE_TO_TIER3_ENGINEERING", null, "Critical SSO outage"),
                    "AI_COPILOT");
            assertEquals("PENDING", pending.getStatus());

            AiApprovalRequest approved = aiService.decideApproval(
                    pending.getId(),
                    new AiOrchestrationService.ApprovalDecisionRequest(true, "Verified production impact; approving Tier-3 escalation."),
                    "sarah.chen");
            assertEquals("APPROVED", approved.getStatus());
            assertNotNull(approved.getExecutionResult());

            Ticket updatedTicket = ticketService.findById(ticket.getId());
            assertEquals("ESCALATED", updatedTicket.getStatus());
            assertTrue(updatedTicket.isEscalated());
        });
    }
}
