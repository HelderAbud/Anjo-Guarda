package com.anjoguarda.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record SaveCustodyPlanRequest(
        @NotBlank String name,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull RuleType ruleType,
        @NotEmpty Map<String, UUID> configuration) {}
