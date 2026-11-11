package com.supportflow.storage;

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
@RequestMapping("/api/tickets/{ticketId}/attachments")
public class StorageController {

    private final StorageService storageService;
    private final AccessControlService accessControlService;

    public StorageController(StorageService storageService, AccessControlService accessControlService) {
        this.storageService = storageService;
        this.accessControlService = accessControlService;
    }

    @GetMapping
    public List<TicketAttachment> listAttachments(@PathVariable Long ticketId) {
        return storageService.listAttachments(ticketId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketAttachment registerAttachment(
            @PathVariable Long ticketId,
            @Valid @RequestBody StorageService.RegisterAttachmentRequest request,
            Authentication auth) {
        String actor = accessControlService.requireActorUsername(auth);
        return storageService.registerAttachment(ticketId, request, actor);
    }
}
