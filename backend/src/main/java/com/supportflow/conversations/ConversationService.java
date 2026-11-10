package com.supportflow.conversations;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.supportflow.audit.AuditService;
import com.supportflow.identity.security.AccessControlService;
import com.supportflow.tickets.Ticket;
import com.supportflow.tickets.TicketService;

@Service
public class ConversationService {

    public record AddMessageRequest(
            @NotBlank(message = "Message content is required") String content,
            String senderType,
            String senderName,
            Boolean internalNote,
            Boolean aiGenerated,
            String citationsJson
    ) {}

    private final ConversationMessageRepository messageRepository;
    private final TicketService ticketService;
    private final AuditService auditService;
    private final AccessControlService accessControlService;

    public ConversationService(
            ConversationMessageRepository messageRepository,
            TicketService ticketService,
            AuditService auditService,
            AccessControlService accessControlService) {
        this.messageRepository = messageRepository;
        this.ticketService = ticketService;
        this.auditService = auditService;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public List<ConversationMessage> getMessagesForTicket(Long ticketId) {
        ticketService.findById(ticketId);
        List<ConversationMessage> allMessages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        AccessControlService.CallerContext caller = accessControlService.currentCaller();
        if (caller.httpAuthenticated() && caller.isCustomer()) {
            return allMessages.stream()
                    .filter(msg -> !msg.isInternalNote())
                    .toList();
        }
        return allMessages;
    }

    @Transactional
    public ConversationMessage addMessage(Long ticketId, AddMessageRequest request, String actor) {
        Ticket ticket = ticketService.findById(ticketId);
        AccessControlService.CallerContext caller = accessControlService.currentCaller();
        boolean isCustomerCaller = caller.httpAuthenticated() && caller.isCustomer();

        ConversationMessage msg = new ConversationMessage();
        msg.setTicketId(ticket.getId());
        msg.setContent(request.content().trim());

        if (isCustomerCaller) {
            msg.setSenderType("CUSTOMER");
            msg.setSenderName(caller.username());
            msg.setInternalNote(false);
            msg.setAiGenerated(false);
            msg.setCitationsJson(null);
        } else {
            msg.setSenderType(request.senderType() != null && !request.senderType().isBlank()
                    ? request.senderType().trim().toUpperCase()
                    : "AGENT");
            msg.setSenderName(request.senderName() != null && !request.senderName().isBlank()
                    ? request.senderName().trim()
                    : actor);
            msg.setInternalNote(Boolean.TRUE.equals(request.internalNote()));
            msg.setAiGenerated(Boolean.TRUE.equals(request.aiGenerated()));
            msg.setCitationsJson(request.citationsJson());
        }

        ConversationMessage saved = messageRepository.save(msg);
        auditService.recordEvent("CONVERSATION_MESSAGE_ADDED", actor, "TICKET", ticket.getTicketKey(),
                (saved.isInternalNote() ? "Internal note" : "Reply") + " added by " + saved.getSenderName());
        return saved;
    }
}
