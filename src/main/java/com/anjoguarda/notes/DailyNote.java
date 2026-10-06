package com.anjoguarda.notes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_notes")
public class DailyNote {

    @Id
    private UUID id;

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected DailyNote() {}

    public DailyNote(UUID id, UUID childId, LocalDate date, String category, String text, UUID createdBy, Instant now) {
        this.id = id;
        this.childId = childId;
        this.date = date;
        this.category = category;
        this.text = text;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void revise(String category, String text, Instant updatedAt) {
        this.category = category;
        this.text = text;
        this.updatedAt = updatedAt;
    }

    public void delete(Instant deletedAt) {
        this.deletedAt = deletedAt;
        this.updatedAt = deletedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChildId() {
        return childId;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getCategory() {
        return category;
    }

    public String getText() {
        return text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
