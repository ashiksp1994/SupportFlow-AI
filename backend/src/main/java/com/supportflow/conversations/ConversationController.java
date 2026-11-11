package com.supportflow.conversations;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.supportflow.identity.security.AccessControlService;

@RestController
@RequestMapping("/api/tickets/{ticketId}/messages")
public class ConversationController {

    private final ConversationService conversationService;
    private final AccessControlService accessControlService;

    public ConversationController(ConversationService conversationService, AccessControlService accessControlService) {
        this.conversationService = conversationService;
        this.accessControlService = accessControlService;
    }

    @GetMapping
    public List<ConversationMessage> listMessages(@PathVariable Long ticketId) {
        return conversationService.getMessagesForTicket(ticketId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationMessage postMessage(
            @PathVariable Long ticketId,
            @Valid @RequestBody ConversationService.AddMessageRequest request,
            Authentication auth) {
        String actor = accessControlService.requireActorUsername(auth);
        return conversationService.addMessage(ticketId, request, actor);
    }
}
