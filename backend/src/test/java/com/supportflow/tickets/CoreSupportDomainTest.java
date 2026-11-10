package com.supportflow.tickets;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.supportflow.SupportFlowApplication;
import com.supportflow.conversations.ConversationMessage;
import com.supportflow.conversations.ConversationService;
import com.supportflow.customers.CustomerProfile;
import com.supportflow.customers.CustomerService;
import com.supportflow.organisations.Organisation;
import com.supportflow.organisations.OrganisationService;
import com.supportflow.storage.TicketAttachment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = SupportFlowApplication.class)
@ActiveProfiles("test")
class CoreSupportDomainTest {

    @Autowired
    private OrganisationService organisationService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private TicketAttachment.StorageService storageService;

    @Test
    void shouldManageOrganisationsCustomersTicketsMessagesSlasAndAttachments() {
        Organisation org = organisationService.createOrganisation(
                new OrganisationService.CreateOrganisationRequest("Acme Cloud Corp", "acmecloud.io", "ENTERPRISE"),
                "admin");
        assertEquals(4, org.getResolutionSlaHours());
        assertEquals(15, org.getResponseSlaMinutes());

        CustomerProfile customer = customerService.createCustomer(
                new CustomerService.CreateCustomerRequest(
                        "Elena Vance", "elena@acmecloud.io", "+1-555-0199", "VP of Infrastructure", org.getId(), true),
                "admin");
        assertEquals("Acme Cloud Corp", customer.getOrganisationName());

        Ticket ticket = ticketService.createTicket(
                new TicketService.CreateTicketRequest(
                        "Production SSO SAML assertion failure after IdP certificate rotation",
                        "Users in our EU tenant receive HTTP 401 SAML signature validation errors.",
                        "HIGH",
                        "SECURITY",
                        customer.getId(),
                        "sarah.chen"),
                "elena@acmecloud.io");
        assertNotNull(ticket.getTicketKey());
        assertEquals("Acme Cloud Corp", ticket.getOrganisationName());
        assertFalse(ticket.isSlaBreached());

        ConversationMessage reply = conversationService.addMessage(
                ticket.getId(),
                new ConversationService.AddMessageRequest(
                        "We are inspecting the X.509 certificate thumbprint in your tenant SAML metadata.",
                        "AGENT",
                        "Sarah Chen",
                        false,
                        false,
                        null),
                "sarah.chen");
        assertEquals(ticket.getId(), reply.getTicketId());

        TicketAttachment attachment = storageService.registerAttachment(
                ticket.getId(),
                new TicketAttachment.StorageService.RegisterAttachmentRequest("idp-metadata.xml", "application/xml", 4096L),
                "elena@acmecloud.io");
        assertTrue(attachment.getPresignedDownloadUrl().contains("idp-metadata.xml"));

        Ticket escalated = ticketService.escalateTicket(ticket.getId(), "Enterprise tenant login outage", "sarah.chen");
        assertTrue(escalated.isEscalated());
        assertEquals("ESCALATED", escalated.getStatus());

        List<TicketService.AgentWorkloadItem> workloads = ticketService.getAgentWorkloads();
        assertFalse(workloads.isEmpty());
    }
}
