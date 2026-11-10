package com.supportflow.tickets;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByTicketKey(String ticketKey);
    List<Ticket> findAllByOrderByCreatedAtDesc();
    List<Ticket> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);
    List<Ticket> findByStatusOrderByCreatedAtDesc(String status);
    List<Ticket> findByAssignedAgentUsernameOrderByCreatedAtDesc(String assignedAgentUsername);
    long countByStatus(String status);
    long countByPriority(String priority);
    long countBySlaBreachedTrue();
    long countByEscalatedTrue();
    long countByAiTriagedTrue();
}
