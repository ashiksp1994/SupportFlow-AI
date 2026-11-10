package com.supportflow.audit;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog recordEvent(String eventType, String actorUsername, String targetType, String targetId, String details) {
        String safeActor = (actorUsername == null || actorUsername.isBlank()) ? "SYSTEM" : actorUsername;
        AuditLog log = new AuditLog(eventType, safeActor, targetType, targetId, details);
        return auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecentLogs() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public long countLogs() {
        return auditLogRepository.count();
    }
}
