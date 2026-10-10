package com.anjoguarda.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class StoredDocument {

    @Id
    private UUID id;

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String filename;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private long size;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected StoredDocument() {}

    public StoredDocument(
            UUID id,
            UUID childId,
            String category,
            String filename,
            String storageKey,
            String mimeType,
            long size,
            UUID createdBy) {
        this.id = id;
        this.childId = childId;
        this.category = category;
        this.filename = filename;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.size = size;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChildId() {
        return childId;
    }

    public String getCategory() {
        return category;
    }

    public String getFilename() {
        return filename;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSize() {
        return size;
    }
}
