package com.supportflow.ai;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiApprovalRepository extends JpaRepository<AiApprovalRequest, Long> {
    List<AiApprovalRequest> findAllByOrderByCreatedAtDesc();
    List<AiApprovalRequest> findByStatusOrderByCreatedAtDesc(String status);
    List<AiApprovalRequest> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
    long countByStatus(String status);
}
