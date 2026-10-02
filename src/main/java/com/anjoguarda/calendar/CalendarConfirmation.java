package com.anjoguarda.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "calendar_confirmations")
public class CalendarConfirmation {

    @Id
    private UUID id;

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String status;

    @Column(name = "realized_guardian_id")
    private UUID realizedGuardianId;

    @Column(name = "realized_start_time")
    private LocalTime realizedStartTime;

    @Column(name = "realized_end_time")
    private LocalTime realizedEndTime;

    private String note;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CalendarConfirmation() {}

    public CalendarConfirmation(
            UUID id,
            UUID childId,
            LocalDate date,
            ConfirmationStatus status,
            UUID realizedGuardianId,
            LocalTime realizedStartTime,
            LocalTime realizedEndTime,
            String note,
            UUID createdBy) {
        this.id = id;
        this.childId = childId;
        this.date = date;
        this.status = status.name();
        this.realizedGuardianId = realizedGuardianId;
        this.realizedStartTime = realizedStartTime;
        this.realizedEndTime = realizedEndTime;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void update(
            ConfirmationStatus status,
            UUID realizedGuardianId,
            LocalTime realizedStartTime,
            LocalTime realizedEndTime,
            String note) {
        this.status = status.name();
        this.realizedGuardianId = realizedGuardianId;
        this.realizedStartTime = realizedStartTime;
        this.realizedEndTime = realizedEndTime;
        this.note = note;
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

    public String getStatus() {
        return status;
    }

    public UUID getRealizedGuardianId() {
        return realizedGuardianId;
    }

    public LocalTime getRealizedStartTime() {
        return realizedStartTime;
    }

    public LocalTime getRealizedEndTime() {
        return realizedEndTime;
    }

    public String getNote() {
        return note;
    }
}
