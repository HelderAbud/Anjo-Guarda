package com.anjoguarda.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "monthly_reports")
public class MonthlyReport {

    @Id
    private UUID id;

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month;

    @Column(nullable = false)
    private int version;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_snapshot", nullable = false, columnDefinition = "jsonb")
    private String contentSnapshot;

    protected MonthlyReport() {}

    public MonthlyReport(
            UUID id,
            UUID childId,
            int year,
            int month,
            int version,
            Instant generatedAt,
            String contentSnapshot) {
        this.id = id;
        this.childId = childId;
        this.year = year;
        this.month = month;
        this.version = version;
        this.generatedAt = generatedAt;
        this.contentSnapshot = contentSnapshot;
    }

    public UUID getId() {
        return id;
    }

    public UUID getChildId() {
        return childId;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public int getVersion() {
        return version;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public String getContentSnapshot() {
        return contentSnapshot;
    }
}
