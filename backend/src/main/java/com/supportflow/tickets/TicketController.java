package com.supportflow.tickets;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public List<Ticket> listTickets(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        List<Ticket> all = ticketService.findAll(status);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));
        int fromIndex = Math.min(all.size(), safePage * safeSize);
        int toIndex = Math.min(all.size(), fromIndex + safeSize);
        return all.subList(fromIndex, toIndex);
    }

    @GetMapping("/{id}")
    public Ticket getTicket(@PathVariable Long id) {
        return ticketService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Ticket createTicket(@Valid @RequestBody TicketService.CreateTicketRequest request, Authentication auth) {
        String actor = auth != null ? auth.getName() : "SYSTEM";
        return ticketService.createTicket(request, actor);
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public Ticket assignAgent(
            @PathVariable Long id,
            @Valid @RequestBody TicketService.AssignAgentRequest request,
            Authentication auth) {
        String actor = auth != null ? auth.getName() : "SYSTEM";
        return ticketService.assignAgent(id, request.agentUsername(), actor);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public Ticket updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody TicketService.UpdateTicketStatusRequest request,
            Authentication auth) {
        String actor = auth != null ? auth.getName() : "SYSTEM";
        return ticketService.updateStatusAndPriority(id, request, actor);
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public Ticket escalateTicket(
            @PathVariable Long id,
            @RequestBody(required = false) TicketService.EscalateTicketRequest request,
            Authentication auth) {
        String actor = auth != null ? auth.getName() : "SYSTEM";
        String reason = request != null ? request.reason() : null;
        return ticketService.escalateTicket(id, reason, actor);
    }

    @GetMapping("/workload")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public List<TicketService.AgentWorkloadItem> getAgentWorkloads() {
        return ticketService.getAgentWorkloads();
    }
}
