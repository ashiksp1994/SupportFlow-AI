package com.supportflow.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.supportflow.audit.AuditService;
import com.supportflow.tickets.Ticket;
import com.supportflow.tickets.TicketService;

@Service
public class StorageService {

    private static final long MAX_ATTACHMENT_SIZE_BYTES = 25L * 1024 * 1024; // 25 MiB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/xml",
            "text/xml",
            "text/plain",
            "text/csv",
            "application/json",
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    public record RegisterAttachmentRequest(
            @NotBlank(message = "File name is required") String fileName,
            @NotBlank(message = "Content type is required") String contentType,
            @Min(value = 1, message = "Size must be positive") long sizeBytes
    ) {}

    private final TicketAttachmentRepository attachmentRepository;
    private final TicketService ticketService;
    private final AuditService auditService;
    private final String s3Endpoint;
    private final String s3Bucket;

    public StorageService(
            TicketAttachmentRepository attachmentRepository,
            TicketService ticketService,
            AuditService auditService,
            @Value("${app.storage.s3-endpoint:http://localhost:9000}") String s3Endpoint,
            @Value("${app.storage.s3-bucket:supportflow-attachments}") String s3Bucket) {
        this.attachmentRepository = attachmentRepository;
        this.ticketService = ticketService;
        this.auditService = auditService;
        this.s3Endpoint = s3Endpoint;
        this.s3Bucket = s3Bucket;
    }

    @Transactional(readOnly = true)
    public List<TicketAttachment> listAttachments(Long ticketId) {
        ticketService.findById(ticketId);
        return attachmentRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    @Transactional
    public TicketAttachment registerAttachment(Long ticketId, RegisterAttachmentRequest request, String actor) {
        Ticket ticket = ticketService.findById(ticketId);
        if (request.sizeBytes() > MAX_ATTACHMENT_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment exceeds maximum size of 25 MiB");
        }
        String normalizedContentType = request.contentType().trim().toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported attachment content type: " + normalizedContentType);
        }

        String safeFile = request.fileName().trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeFile.isBlank() || safeFile.startsWith(".")) {
            safeFile = "attachment_" + safeFile;
        }
        String storageKey = "tickets/" + ticket.getTicketKey() + "/" + UUID.randomUUID() + "/" + safeFile;
        long expiresEpochSeconds = Instant.now().plusSeconds(3600).getEpochSecond();
        String signature = computeSignature(storageKey, expiresEpochSeconds);

        TicketAttachment attachment = new TicketAttachment();
        attachment.setTicketId(ticket.getId());
        attachment.setFileName(safeFile);
        attachment.setContentType(normalizedContentType);
        attachment.setSizeBytes(request.sizeBytes());
        attachment.setStorageBucket(s3Bucket);
        attachment.setStorageKey(storageKey);
        attachment.setPresignedDownloadUrl(
                s3Endpoint + "/" + s3Bucket + "/" + storageKey + "?X-Amz-Expires=3600&X-Amz-Signature=" + signature);
        attachment.setUploadedBy(actor);

        TicketAttachment saved = attachmentRepository.save(attachment);
        auditService.recordEvent("ATTACHMENT_UPLOADED", actor, "TICKET", ticket.getTicketKey(),
                "Registered S3 attachment metadata for " + safeFile + " (" + request.sizeBytes() + " bytes)");
        return saved;
    }

    private String computeSignature(String storageKey, long expiresEpochSeconds) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((storageKey + ":" + expiresEpochSeconds).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (Exception ex) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
