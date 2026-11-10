package com.supportflow.ai;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ai_approval_requests")
public class AiApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "ticket_key", nullable = false, length = 32)
    private String ticketKey;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @Column(name = "action_summary", nullable = false, length = 500)
    private String actionSummary;

    @Column(name = "parameters_json", nullable = false, columnDefinition = "TEXT")
    private String parametersJson;

    @Column(name = "ai_reasoning", nullable = false, columnDefinition = "TEXT")
    private String aiReasoning;

    @Column(nullable = false)
    private double confidence = 0.90;

    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "requested_by", nullable = false)
    private String requestedBy = "AI_AGENT";

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "execution_result", columnDefinition = "TEXT")
    private String executionResult;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    @Column(nullable = false)
    private long version = 0L;
}
