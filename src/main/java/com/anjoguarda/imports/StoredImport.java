package com.anjoguarda.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "imports")
public class StoredImport {

    @Id
    private UUID id;

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(nullable = false)
    private String status;

    @Column(name = "extracted_text", nullable = false, columnDefinition = "text")
    private String extractedText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StoredImport() {}

    public StoredImport(
            UUID id,
            UUID childId,
            String filename,
            String storageKey,
            String sourceType,
            String extractedText,
            Instant createdAt) {
        this.id = id;
        this.childId = childId;
        this.filename = filename;
        this.storageKey = storageKey;
        this.sourceType = sourceType;
        this.status = "EXTRAIDO";
        this.extractedText = extractedText;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChildId() {
        return childId;
    }

    public String getFilename() {
        return filename;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getStatus() {
        return status;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
