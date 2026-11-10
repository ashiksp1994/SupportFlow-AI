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

@RestController
@RequestMapping("/api/tickets/{ticketId}/attachments")
public class StorageController {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
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
        String actor = auth != null ? auth.getName() : "SYSTEM";
        return storageService.registerAttachment(ticketId, request, actor);
    }
}
