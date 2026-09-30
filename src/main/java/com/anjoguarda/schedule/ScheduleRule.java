package com.anjoguarda.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "schedule_rules")
public class ScheduleRule {

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "rule_type", nullable = false)
    private String ruleType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration_json", nullable = false)
    private String configurationJson;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    protected ScheduleRule() {}

    public ScheduleRule(
            UUID id,
            UUID planId,
            RuleType ruleType,
            String configurationJson,
            LocalDate startDate,
            LocalDate endDate) {
        this.id = id;
        this.planId = planId;
        this.ruleType = ruleType.name();
        this.configurationJson = configurationJson;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public UUID getId() {
        return id;
    }

    public String getRuleType() {
        return ruleType;
    }

    public String getConfigurationJson() {
        return configurationJson;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
