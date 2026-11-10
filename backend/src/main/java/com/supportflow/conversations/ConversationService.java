package com.supportflow.conversations;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.supportflow.audit.AuditService;
import com.supportflow.tickets.Ticket;
import com.supportflow.tickets.TicketService;

@Service
public class ConversationService {

    public record AddMessageRequest(
            @NotBlank(message = "Message content is required") String content,
            String senderType,
            String senderName,
            boolean internalNote,
            boolean aiGenerated,
            String citationsJson
    ) {}

    private final ConversationMessageRepository messageRepository;
    private final TicketService ticketService;
    private final AuditService auditService;

    public ConversationService(
            ConversationMessageRepository messageRepository,
            TicketService ticketService,
            AuditService auditService) {
        this.messageRepository = messageRepository;
        this.ticketService = ticketService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ConversationMessage> getMessagesForTicket(Long ticketId) {
        ticketService.findById(ticketId);
        return messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional
    public ConversationMessage addMessage(Long ticketId, AddMessageRequest request, String actor) {
        Ticket ticket = ticketService.findById(ticketId);
        ConversationMessage msg = new ConversationMessage();
        msg.setTicketId(ticket.getId());
        msg.setContent(request.content().trim());
        msg.setSenderType(request.senderType() != null && !request.senderType().isBlank()
                ? request.senderType().trim().toUpperCase()
                : "AGENT");
        msg.setSenderName(request.senderName() != null && !request.senderName().isBlank()
                ? request.senderName().trim()
                : actor);
        msg.setInternalNote(request.internalNote());
        msg.setAiGenerated(request.aiGenerated());
        msg.setCitationsJson(request.citationsJson());

        ConversationMessage saved = messageRepository.save(msg);
        auditService.recordEvent("CONVERSATION_MESSAGE_ADDED", actor, "TICKET", ticket.getTicketKey(),
                (saved.isInternalNote() ? "Internal note" : "Reply") + " added by " + saved.getSenderName());
        return saved;
    }

    @RestController
    @RequestMapping("/api/tickets/{ticketId}/messages")
    public static class ConversationController {

        private final ConversationService conversationService;

        public ConversationController(ConversationService conversationService) {
            this.conversationService = conversationService;
        }

        @GetMapping
        public List<ConversationMessage> listMessages(@PathVariable Long ticketId) {
            return conversationService.getMessagesForTicket(ticketId);
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public ConversationMessage postMessage(
                @PathVariable Long ticketId,
                @Valid @RequestBody AddMessageRequest request,
                Authentication auth) {
            String actor = auth != null ? auth.getName() : "SYSTEM";
            return conversationService.addMessage(ticketId, request, actor);
        }
    }
}
