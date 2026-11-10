package com.supportflow.storage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {
    List<TicketAttachment> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
}
