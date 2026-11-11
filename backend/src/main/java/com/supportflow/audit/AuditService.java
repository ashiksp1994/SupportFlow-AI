package com.supportflow.audit;

import java.util.List;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
        List<AuditLog> recent = auditLogRepository.findTop100ByOrderByCreatedAtDesc();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return List.of();
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        if (isAdmin) {
            return recent;
        }
        String username = auth.getName();
        return recent.stream()
                .filter(log -> username != null && username.equalsIgnoreCase(log.getActorUsername()))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countLogs() {
        return auditLogRepository.count();
    }

    @Transactional(readOnly = true)
    public long countLogsForScope(String actorUsername, Set<String> accessibleTicketKeys, Long organisationId) {
        String orgIdStr = organisationId != null ? String.valueOf(organisationId) : null;
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(log -> (actorUsername != null && actorUsername.equalsIgnoreCase(log.getActorUsername()))
                        || ("TICKET".equals(log.getTargetType()) && accessibleTicketKeys != null && accessibleTicketKeys.contains(log.getTargetId()))
                        || ("ORGANISATION".equals(log.getTargetType()) && orgIdStr != null && orgIdStr.equals(log.getTargetId())))
                .count();
    }
}
