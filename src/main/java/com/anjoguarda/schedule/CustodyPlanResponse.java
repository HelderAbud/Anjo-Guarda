package com.anjoguarda.schedule;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record CustodyPlanResponse(
        UUID id,
        UUID childId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        RuleResponse rule) {

    public record RuleResponse(
            UUID id,
            String ruleType,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, UUID> configuration) {}
}
