package com.supportflow.tickets;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_key", nullable = false, unique = true, length = 32)
    private String ticketKey;

    @Column(nullable = false, length = 300)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 64)
    private String status = "OPEN";

    @Column(nullable = false, length = 64)
    private String priority = "MEDIUM";

    @Column(nullable = false, length = 64)
    private String category = "GENERAL";

    @Column(nullable = false, length = 64)
    private String sentiment = "NEUTRAL";

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "organisation_id")
    private Long organisationId;

    @Column(name = "organisation_name")
    private String organisationName;

    @Column(name = "assigned_agent_username")
    private String assignedAgentUsername;

    @Column(name = "sla_due_at", nullable = false)
    private Instant slaDueAt = Instant.now().plus(24, ChronoUnit.HOURS);

    @Column(name = "sla_breached", nullable = false)
    private boolean slaBreached = false;

    @Column(nullable = false)
    private boolean escalated = false;

    @Column(name = "ai_triaged", nullable = false)
    private boolean aiTriaged = false;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
