package com.supportflow.tickets;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotBlank;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.audit.AuditService;
import com.supportflow.customers.CustomerProfile;
import com.supportflow.customers.CustomerService;
import com.supportflow.identity.security.AccessControlService;
import com.supportflow.organisations.Organisation;
import com.supportflow.organisations.OrganisationService;

@Service
public class TicketService {

    public record CreateTicketRequest(
            @NotBlank(message = "Subject is required") String subject,
            @NotBlank(message = "Description is required") String description,
            String priority,
            String category,
            Long customerId,
            String assignedAgentUsername
    ) {}

    public record AssignAgentRequest(@NotBlank(message = "Agent username is required") String agentUsername) {}

    public record UpdateTicketStatusRequest(
            @NotBlank(message = "Status is required") String status,
            String priority,
            String category
    ) {}

    public record EscalateTicketRequest(String reason) {}

    public record AgentWorkloadItem(
            String agentUsername,
            long openTickets,
            long highOrCriticalTickets,
            long escalatedTickets
    ) {}

    private final TicketRepository ticketRepository;
    private final CustomerService customerService;
    private final OrganisationService organisationService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final AccessControlService accessControlService;

    public TicketService(
            TicketRepository ticketRepository,
            CustomerService customerService,
            OrganisationService organisationService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher,
            AccessControlService accessControlService) {
        this.ticketRepository = ticketRepository;
        this.customerService = customerService;
        this.organisationService = organisationService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public List<Ticket> findAll(String status) {
        List<Ticket> tickets = (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status))
                ? ticketRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase())
                : ticketRepository.findAllByOrderByCreatedAtDesc();
        Instant now = Instant.now();
        for (Ticket t : tickets) {
            if (!"RESOLVED".equals(t.getStatus()) && !"CLOSED".equals(t.getStatus()) && now.isAfter(t.getSlaDueAt())) {
                t.setSlaBreached(true);
            }
        }
        return tickets.stream()
                .filter(t -> accessControlService.canAccessTenant(t.getOrganisationId(), t.getCustomerEmail()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Ticket findById(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found: " + id));
        accessControlService.verifyTenantAccess(ticket.getOrganisationId(), ticket.getCustomerEmail());
        return ticket;
    }

    @Transactional
    public Ticket createTicket(CreateTicketRequest request, String actor) {
        AccessControlService.CallerContext caller = accessControlService.currentCaller();
        Ticket ticket = new Ticket();
        long nextNumber = ticketRepository.count() + 1001;
        ticket.setTicketKey("SF-" + nextNumber);
        ticket.setSubject(request.subject().trim());
        ticket.setDescription(request.description().trim());
        ticket.setStatus("OPEN");
        ticket.setPriority(request.priority() != null && !request.priority().isBlank()
                ? request.priority().trim().toUpperCase()
                : "MEDIUM");
        ticket.setCategory(request.category() != null && !request.category().isBlank()
                ? request.category().trim().toUpperCase()
                : "GENERAL");

        if (!caller.httpAuthenticated() || caller.isStaff()) {
            ticket.setAssignedAgentUsername(request.assignedAgentUsername());
        }

        int slaHours = 24;
        if (request.customerId() != null) {
            CustomerProfile customer = customerService.findById(request.customerId());
            ticket.setCustomerId(customer.getId());
            ticket.setCustomerName(customer.getFullName());
            ticket.setCustomerEmail(customer.getEmail());
            ticket.setOrganisationId(customer.getOrganisationId());
            ticket.setOrganisationName(customer.getOrganisationName());
            if (customer.getOrganisationId() != null) {
                Organisation org = organisationService.findById(customer.getOrganisationId());
                slaHours = org.getResolutionSlaHours();
            }
            if (customer.isVip() && slaHours > 4) {
                slaHours = 4;
            }
        } else if (caller.httpAuthenticated()) {
            ticket.setCustomerName(caller.username());
            ticket.setCustomerEmail(caller.email() != null ? caller.email() : caller.username());
            if (caller.organisationId() != null) {
                ticket.setOrganisationId(caller.organisationId());
                Organisation org = organisationService.findById(caller.organisationId());
                ticket.setOrganisationName(org.getName());
                slaHours = org.getResolutionSlaHours();
            }
        } else if (actor != null && !actor.isBlank()) {
            ticket.setCustomerEmail(actor);
        }

        if ("CRITICAL".equals(ticket.getPriority())) {
            slaHours = Math.min(slaHours, 2);
        } else if ("HIGH".equals(ticket.getPriority())) {
            slaHours = Math.min(slaHours, 8);
        }

        ticket.setSlaDueAt(Instant.now().plus(slaHours, ChronoUnit.HOURS));
        Ticket saved = ticketRepository.save(ticket);

        auditService.recordEvent("TICKET_CREATED", actor, "TICKET", saved.getTicketKey(),
                "Created ticket '" + saved.getSubject() + "' with priority " + saved.getPriority());
        eventPublisher.publishEvent(new TicketEvents.TicketCreatedEvent(
                saved.getId(), saved.getTicketKey(), saved.getSubject(), saved.getPriority(), actor));

        return saved;
    }

    @Transactional
    public Ticket assignAgent(Long id, String agentUsername, String actor) {
        accessControlService.requireStaff();
        Ticket ticket = findById(id);
        ticket.setAssignedAgentUsername(agentUsername.trim());
        if ("OPEN".equals(ticket.getStatus())) {
            ticket.setStatus("IN_PROGRESS");
        }
        ticket.setUpdatedAt(Instant.now());
        Ticket saved = ticketRepository.save(ticket);

        auditService.recordEvent("TICKET_ASSIGNED", actor, "TICKET", saved.getTicketKey(),
                "Assigned ticket to agent " + agentUsername);
        return saved;
    }

    @Transactional
    public Ticket updateStatusAndPriority(Long id, UpdateTicketStatusRequest request, String actor) {
        accessControlService.requireStaff();
        Ticket ticket = findById(id);
        ticket.setStatus(request.status().trim().toUpperCase());
        if (request.priority() != null && !request.priority().isBlank()) {
            ticket.setPriority(request.priority().trim().toUpperCase());
        }
        if (request.category() != null && !request.category().isBlank()) {
            ticket.setCategory(request.category().trim().toUpperCase());
        }
        ticket.setUpdatedAt(Instant.now());
        Ticket saved = ticketRepository.save(ticket);

        auditService.recordEvent("TICKET_UPDATED", actor, "TICKET", saved.getTicketKey(),
                "Updated status=" + saved.getStatus() + ", priority=" + saved.getPriority());
        return saved;
    }

    @Transactional
    public Ticket escalateTicket(Long id, String reason, String actor) {
        accessControlService.requireStaff();
        Ticket ticket = findById(id);
        ticket.setEscalated(true);
        ticket.setStatus("ESCALATED");
        if ("LOW".equals(ticket.getPriority()) || "MEDIUM".equals(ticket.getPriority())) {
            ticket.setPriority("HIGH");
        }
        ticket.setUpdatedAt(Instant.now());
        Ticket saved = ticketRepository.save(ticket);

        String escalationReason = (reason != null && !reason.isBlank()) ? reason : "SLA risk or complex technical escalation";
        auditService.recordEvent("TICKET_ESCALATED", actor, "TICKET", saved.getTicketKey(),
                "Escalated ticket: " + escalationReason);
        eventPublisher.publishEvent(new TicketEvents.TicketEscalatedEvent(
                saved.getId(), saved.getTicketKey(), escalationReason, actor));

        return saved;
    }

    @Transactional
    public Ticket saveTicket(Ticket ticket) {
        ticket.setUpdatedAt(Instant.now());
        return ticketRepository.save(ticket);
    }

    @Transactional(readOnly = true)
    public List<AgentWorkloadItem> getAgentWorkloads() {
        accessControlService.requireStaff();
        List<Ticket> activeTickets = findAll(null).stream()
                .filter(t -> t.getAssignedAgentUsername() != null && !t.getAssignedAgentUsername().isBlank())
                .filter(t -> !"RESOLVED".equals(t.getStatus()) && !"CLOSED".equals(t.getStatus()))
                .toList();

        Map<String, List<Ticket>> byAgent = activeTickets.stream()
                .collect(Collectors.groupingBy(Ticket::getAssignedAgentUsername));

        return byAgent.entrySet().stream()
                .map(entry -> {
                    List<Ticket> list = entry.getValue();
                    long highCrit = list.stream()
                            .filter(t -> "HIGH".equals(t.getPriority()) || "CRITICAL".equals(t.getPriority()))
                            .count();
                    long escalated = list.stream().filter(Ticket::isEscalated).count();
                    return new AgentWorkloadItem(entry.getKey(), list.size(), highCrit, escalated);
                })
                .toList();
    }
}
