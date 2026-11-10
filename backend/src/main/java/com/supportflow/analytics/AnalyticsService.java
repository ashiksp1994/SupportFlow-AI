package com.supportflow.analytics;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.supportflow.ai.AiApprovalRepository;
import com.supportflow.ai.AiOrchestrationService;
import com.supportflow.audit.AuditService;
import com.supportflow.conversations.ConversationService;
import com.supportflow.customers.CustomerProfile;
import com.supportflow.customers.CustomerRepository;
import com.supportflow.customers.CustomerService;
import com.supportflow.identity.model.UserModel;
import com.supportflow.identity.repository.UserRepository;
import com.supportflow.identity.security.AccessControlService;
import com.supportflow.identity.service.UserService;
import com.supportflow.knowledge.KnowledgeArticleRepository;
import com.supportflow.knowledge.KnowledgeService;
import com.supportflow.organisations.Organisation;
import com.supportflow.organisations.OrganisationRepository;
import com.supportflow.organisations.OrganisationService;
import com.supportflow.tickets.Ticket;
import com.supportflow.tickets.TicketRepository;
import com.supportflow.tickets.TicketService;

@Service
public class AnalyticsService {

    public record DashboardAnalytics(
            long totalTickets,
            long openTickets,
            long inProgressTickets,
            long escalatedTickets,
            long resolvedTickets,
            long slaBreachedTickets,
            double slaComplianceRatePercent,
            long aiTriagedTickets,
            long pendingAiApprovals,
            long totalKnowledgeArticles,
            long totalOrganisations,
            long totalCustomers,
            long totalAuditLogs,
            Map<String, Long> ticketsByPriority,
            List<TicketService.AgentWorkloadItem> agentWorkloads
    ) {}

    private final TicketRepository ticketRepository;
    private final TicketService ticketService;
    private final AiApprovalRepository approvalRepository;
    private final KnowledgeArticleRepository knowledgeRepository;
    private final OrganisationRepository organisationRepository;
    private final CustomerRepository customerRepository;
    private final AuditService auditService;
    private final AccessControlService accessControlService;

    public AnalyticsService(
            TicketRepository ticketRepository,
            TicketService ticketService,
            AiApprovalRepository approvalRepository,
            KnowledgeArticleRepository knowledgeRepository,
            OrganisationRepository organisationRepository,
            CustomerRepository customerRepository,
            AuditService auditService,
            AccessControlService accessControlService) {
        this.ticketRepository = ticketRepository;
        this.ticketService = ticketService;
        this.approvalRepository = approvalRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.organisationRepository = organisationRepository;
        this.customerRepository = customerRepository;
        this.auditService = auditService;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public DashboardAnalytics getDashboardAnalytics() {
        accessControlService.requireStaff();
        List<Ticket> scopedTickets = ticketService.findAll(null);
        long total = scopedTickets.size();
        long open = scopedTickets.stream().filter(t -> "OPEN".equals(t.getStatus())).count();
        long inProgress = scopedTickets.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count();
        long escalated = scopedTickets.stream().filter(Ticket::isEscalated).count();
        long resolved = scopedTickets.stream().filter(t -> "RESOLVED".equals(t.getStatus()) || "CLOSED".equals(t.getStatus())).count();
        long breached = scopedTickets.stream().filter(Ticket::isSlaBreached).count();

        double slaCompliance = total == 0 ? 100.0 : Math.round(((double) (total - breached) / total) * 1000.0) / 10.0;
        long aiTriaged = scopedTickets.stream().filter(Ticket::isAiTriaged).count();
        long pendingApprovals = approvalRepository.countByStatus("PENDING");

        Map<String, Long> byPriority = Map.of(
                "CRITICAL", scopedTickets.stream().filter(t -> "CRITICAL".equals(t.getPriority())).count(),
                "HIGH", scopedTickets.stream().filter(t -> "HIGH".equals(t.getPriority())).count(),
                "MEDIUM", scopedTickets.stream().filter(t -> "MEDIUM".equals(t.getPriority())).count(),
                "LOW", scopedTickets.stream().filter(t -> "LOW".equals(t.getPriority())).count()
        );

        return new DashboardAnalytics(
                total,
                open,
                inProgress,
                escalated,
                resolved,
                breached,
                slaCompliance,
                aiTriaged,
                pendingApprovals,
                knowledgeRepository.count(),
                organisationRepository.count(),
                customerRepository.count(),
                auditService.countLogs(),
                byPriority,
                ticketService.getAgentWorkloads()
        );
    }

    @Bean
    public CommandLineRunner seedDemoData(
            @Value("${app.demo.seed-enabled:false}") boolean seedEnabled,
            UserRepository userRepository,
            UserService userService,
            OrganisationRepository organisationRepository,
            OrganisationService organisationService,
            CustomerService customerService,
            TicketService ticketService,
            ConversationService conversationService,
            KnowledgeService knowledgeService,
            AiOrchestrationService aiService) {
        return args -> {
            if (!seedEnabled || organisationRepository.count() > 0) {
                return;
            }

            if (!userRepository.existsByUsername("admin")) {
                UserModel admin = new UserModel();
                admin.setUsername("admin");
                admin.setPassword("SupportFlow!2026");
                admin.setFullName("Ashik Prasanna (Platform Admin)");
                admin.setEmail("admin@supportflow.ai");
                admin.setRole("ADMIN");
                userService.saveUser(admin);
            }

            if (!userRepository.existsByUsername("sarah.chen")) {
                UserModel agent1 = new UserModel();
                agent1.setUsername("sarah.chen");
                agent1.setPassword("SupportFlow!2026");
                agent1.setFullName("Sarah Chen (Principal Support Engineer)");
                agent1.setEmail("sarah.chen@supportflow.ai");
                agent1.setRole("AGENT");
                userService.saveUser(agent1);
            }

            if (!userRepository.existsByUsername("marcus.vance")) {
                UserModel agent2 = new UserModel();
                agent2.setUsername("marcus.vance");
                agent2.setPassword("SupportFlow!2026");
                agent2.setFullName("Marcus Vance (AI Escalation Specialist)");
                agent2.setEmail("marcus.vance@supportflow.ai");
                agent2.setRole("AGENT");
                userService.saveUser(agent2);
            }

            Organisation org1 = organisationService.createOrganisation(
                    new OrganisationService.CreateOrganisationRequest("Nordic FinTech Group", "nordicfintech.eu", "ENTERPRISE"),
                    "SYSTEM");
            Organisation org2 = organisationService.createOrganisation(
                    new OrganisationService.CreateOrganisationRequest("Helios BioLabs", "heliosbiolabs.io", "BUSINESS"),
                    "SYSTEM");

            CustomerProfile cust1 = customerService.createCustomer(
                    new CustomerService.CreateCustomerRequest(
                            "Astrid Lindholm", "astrid.lindholm@nordicfintech.eu", "+46-8-555-0140",
                            "Head of Identity & Security", org1.getId(), true),
                    "SYSTEM");
            CustomerProfile cust2 = customerService.createCustomer(
                    new CustomerService.CreateCustomerRequest(
                            "Dr. Lukas Weber", "l.weber@heliosbiolabs.io", "+49-30-555-0182",
                            "Director of Cloud Operations", org2.getId(), false),
                    "SYSTEM");

            knowledgeService.createArticle(
                    new KnowledgeService.CreateArticleRequest(
                            "SAML 2.0 Certificate Rotation & HTTP 401 Signature Validation Troubleshooting",
                            "SECURITY",
                            "saml,sso,certificate,idp,okta,entra,401",
                            "When rotating an Identity Provider (Okta or Microsoft Entra ID) X.509 signing certificate, upload the new PEM certificate under Organisation Settings -> Security -> Secondary IdP Certificate prior to activating the key in your IdP. If clock skew exceeds 120 seconds, assertions will fail with SAML_SIGNATURE_INVALID."),
                    "sarah.chen");

            knowledgeService.createArticle(
                    new KnowledgeService.CreateArticleRequest(
                            "Webhook Delivery Retries, HMAC-SHA256 Signatures & Rate Limit (HTTP 429) Policy",
                            "INTEGRATION",
                            "webhook,api,rate,limit,429,timeout,hmac",
                            "SupportFlow AI dispatches outbound webhook payloads with an X-SupportFlow-Signature-256 header. Enterprise tenants have a burst limit of 1,200 requests/minute. If an endpoint responds with HTTP 429 or 5xx, exponential backoff retries occur over 6 hours."),
                    "marcus.vance");

            knowledgeService.createArticle(
                    new KnowledgeService.CreateArticleRequest(
                            "Enterprise SLA Service Credit Policy & Automated Billing Adjustments",
                            "BILLING",
                            "billing,sla,refund,credit,invoice,enterprise",
                            "Enterprise SLA Tier customers are guaranteed a 15-minute initial response time and 4-hour P1 resolution target. Verified SLA breaches qualify for a 15% monthly service credit, which requires human supervisor approval before ledger execution."),
                    "admin");

            Ticket t1 = ticketService.createTicket(
                    new TicketService.CreateTicketRequest(
                            "Production SAML SSO login outage after Okta X.509 certificate rotation",
                            "All 1,400 employees in our EU production tenant receive HTTP 401 SAML signature validation errors after rotating our Okta signing certificate this morning.",
                            "CRITICAL",
                            "SECURITY",
                            cust1.getId(),
                            "sarah.chen"),
                    cust1.getEmail());

            conversationService.addMessage(
                    t1.getId(),
                    new ConversationService.AddMessageRequest(
                            "Our EU trading desk is currently unable to authenticate into the portal. Attaching IdP metadata trace.",
                            "CUSTOMER",
                            cust1.getFullName(),
                            false,
                            false,
                            null),
                    cust1.getEmail());

            aiService.triageTicket(t1.getId(), "AI_COPILOT");
            aiService.summarizeConversation(t1.getId(), "AI_COPILOT");
            aiService.proposeToolAction(
                    t1.getId(),
                    new AiOrchestrationService.ProposeToolCallRequest(
                            "ESCALATE_TO_TIER3_ENGINEERING",
                            "{\"ticketKey\":\"" + t1.getTicketKey() + "\",\"tenant\":\"nordicfintech.eu\",\"severity\":\"P1\"}",
                            "Enterprise VIP customer reporting production SAML SSO outage affecting 1,400 users."),
                    "AI_COPILOT");

            Ticket t2 = ticketService.createTicket(
                    new TicketService.CreateTicketRequest(
                            "Request for SLA credit on October invoice & webhook 429 rate limit increase",
                            "We experienced webhook throttling (HTTP 429) during our batch sync and would like to request an SLA billing credit review.",
                            "HIGH",
                            "BILLING",
                            cust2.getId(),
                            "marcus.vance"),
                    cust2.getEmail());

            aiService.triageTicket(t2.getId(), "AI_COPILOT");
            aiService.proposeToolAction(
                    t2.getId(),
                    new AiOrchestrationService.ProposeToolCallRequest(
                            "ISSUE_SLA_CREDIT_REFUND",
                            "{\"ticketKey\":\"" + t2.getTicketKey() + "\",\"creditPercent\":15}",
                            "Customer requested billing credit adjustment; consequential financial action requires human approval."),
                    "AI_COPILOT");
        };
    }
}
