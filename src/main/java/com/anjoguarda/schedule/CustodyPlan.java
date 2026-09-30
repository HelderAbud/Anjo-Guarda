package com.anjoguarda.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "custody_plans")
public class CustodyPlan {

    @Id
    private UUID id;

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active;

    protected CustodyPlan() {}

    public CustodyPlan(UUID id, UUID childId, String name, LocalDate startDate, LocalDate endDate) {
        this.id = id;
        this.childId = childId;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChildId() {
        return childId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isActive() {
        return active;
    }
}
