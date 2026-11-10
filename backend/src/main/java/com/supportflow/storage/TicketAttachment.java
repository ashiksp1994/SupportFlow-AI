package com.supportflow.storage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
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

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ticket_attachments")
public class TicketAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "storage_bucket", nullable = false, length = 128)
    private String storageBucket;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "presigned_download_url", length = 1024)
    private String presignedDownloadUrl;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Service
    public static class StorageService {

        public record RegisterAttachmentRequest(
                @NotBlank(message = "File name is required") String fileName,
                @NotBlank(message = "Content type is required") String contentType,
                @Positive(message = "File size must be positive") long fileSizeBytes
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
                @Value("${app.storage.bucket:supportflow-attachments}") String s3Bucket) {
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
            String sanitizedFile = request.fileName().replaceAll("[^a-zA-Z0-9._-]", "_");
            String storageKey = "tickets/" + ticket.getTicketKey() + "/" + UUID.randomUUID() + "-" + sanitizedFile;

            TicketAttachment attachment = new TicketAttachment();
            attachment.setTicketId(ticketId);
            attachment.setFileName(sanitizedFile);
            attachment.setContentType(request.contentType());
            attachment.setFileSizeBytes(request.fileSizeBytes());
            attachment.setStorageBucket(s3Bucket);
            attachment.setStorageKey(storageKey);
            attachment.setPresignedDownloadUrl(s3Endpoint + "/" + s3Bucket + "/" + storageKey + "?presigned=1");
            attachment.setUploadedBy(actor);

            TicketAttachment saved = attachmentRepository.save(attachment);
            auditService.recordEvent("ATTACHMENT_UPLOADED", actor, "TICKET", ticket.getTicketKey(),
                    "Registered S3 attachment " + sanitizedFile + " (" + request.fileSizeBytes() + " bytes)");
            return saved;
        }
    }

    @RestController
    @RequestMapping("/api/tickets/{ticketId}/attachments")
    public static class StorageController {

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
        public TicketAttachment uploadAttachmentMetadata(
                @PathVariable Long ticketId,
                @Valid @RequestBody StorageService.RegisterAttachmentRequest request,
                Authentication auth) {
            String actor = auth != null ? auth.getName() : "SYSTEM";
            return storageService.registerAttachment(ticketId, request, actor);
        }
    }
}
