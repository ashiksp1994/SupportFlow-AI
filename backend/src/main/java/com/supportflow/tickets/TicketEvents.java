package com.supportflow.tickets;

public final class TicketEvents {

    private TicketEvents() {}

    public record TicketCreatedEvent(
            Long ticketId,
            String ticketKey,
            String subject,
            String priority,
            String actor
    ) {}

    public record TicketEscalatedEvent(
            Long ticketId,
            String ticketKey,
            String reason,
            String actor
    ) {}
}
