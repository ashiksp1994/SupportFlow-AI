package com.supportflow.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.supportflow.tickets.TicketEvents;

@Component
public class SupportNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SupportNotificationListener.class);

    @EventListener
    public void onTicketCreated(TicketEvents.TicketCreatedEvent event) {
        log.info("[Notification] Ticket created: {} ({}) [priority={}] by {}",
                event.ticketKey(), event.subject(), event.priority(), event.actor());
    }

    @EventListener
    public void onTicketEscalated(TicketEvents.TicketEscalatedEvent event) {
        log.warn("[Notification] SLA / Engineering Escalation for ticket {}: reason='{}' triggered by {}",
                event.ticketKey(), event.reason(), event.actor());
    }
}
