package com.supportflow.storage;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_bucket", nullable = false, length = 128)
    private String storageBucket = "supportflow-attachments";

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "presigned_download_url", length = 1024)
    private String presignedDownloadUrl;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy = "SYSTEM";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
