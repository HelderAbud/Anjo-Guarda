package com.anjoguarda.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "calendar_exceptions")
public class CalendarException {

    @Id
    private UUID id;

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @Column(nullable = false)
    private LocalDate date;

    private String reason;

    @Column(name = "original_guardian_id")
    private UUID originalGuardianId;

    @Column(name = "new_guardian_id", nullable = false)
    private UUID newGuardianId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CalendarException() {}

    public CalendarException(
            UUID id,
            UUID childId,
            LocalDate date,
            String reason,
            UUID originalGuardianId,
            UUID newGuardianId,
            UUID createdBy) {
        this.id = id;
        this.childId = childId;
        this.date = date;
        this.reason = reason;
        this.originalGuardianId = originalGuardianId;
        this.newGuardianId = newGuardianId;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
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

    public String getReason() {
        return reason;
    }

    public UUID getOriginalGuardianId() {
        return originalGuardianId;
    }

    public UUID getNewGuardianId() {
        return newGuardianId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void update(String reason, UUID originalGuardianId, UUID newGuardianId) {
        this.reason = reason;
        this.originalGuardianId = originalGuardianId;
        this.newGuardianId = newGuardianId;
    }
}
