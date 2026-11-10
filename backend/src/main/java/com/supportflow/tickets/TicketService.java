package com.supportflow.tickets;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.audit.AuditService;
import com.supportflow.customers.CustomerProfile;
import com.supportflow.customers.CustomerService;
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

    public TicketService(
            TicketRepository ticketRepository,
            CustomerService customerService,
            OrganisationService organisationService,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.customerService = customerService;
        this.organisationService = organisationService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
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
        return tickets;
    }

    @Transactional(readOnly = true)
    public Ticket findById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found: " + id));
    }

    @Transactional
    public Ticket createTicket(CreateTicketRequest request, String actor) {
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
        ticket.setAssignedAgentUsername(request.assignedAgentUsername());

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
        List<Ticket> activeTickets = ticketRepository.findAll().stream()
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

    @RestController
    @RequestMapping("/api/tickets")
    public static class TicketController {

        private final TicketService ticketService;

        public TicketController(TicketService ticketService) {
            this.ticketService = ticketService;
        }

        @GetMapping
        public List<Ticket> listTickets(@RequestParam(required = false) String status) {
            return ticketService.findAll(status);
        }

        @GetMapping("/{id}")
        public Ticket getTicket(@PathVariable Long id) {
            return ticketService.findById(id);
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public Ticket createTicket(@Valid @RequestBody CreateTicketRequest request, Authentication auth) {
            String actor = auth != null ? auth.getName() : "SYSTEM";
            return ticketService.createTicket(request, actor);
        }

        @PatchMapping("/{id}/assign")
        public Ticket assignAgent(@PathVariable Long id, @Valid @RequestBody AssignAgentRequest request, Authentication auth) {
            String actor = auth != null ? auth.getName() : "SYSTEM";
            return ticketService.assignAgent(id, request.agentUsername(), actor);
        }

        @PatchMapping("/{id}/status")
        public Ticket updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateTicketStatusRequest request, Authentication auth) {
            String actor = auth != null ? auth.getName() : "SYSTEM";
            return ticketService.updateStatusAndPriority(id, request, actor);
        }

        @PostMapping("/{id}/escalate")
        public Ticket escalateTicket(@PathVariable Long id, @RequestBody(required = false) EscalateTicketRequest request, Authentication auth) {
            String actor = auth != null ? auth.getName() : "SYSTEM";
            String reason = request != null ? request.reason() : null;
            return ticketService.escalateTicket(id, reason, actor);
        }

        @GetMapping("/workload")
        public List<AgentWorkloadItem> getAgentWorkloads() {
            return ticketService.getAgentWorkloads();
        }
    }
}
