package com.anjoguarda.report;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record MonthlyReportResponse(
        UUID id,
        UUID childId,
        int year,
        int month,
        int version,
        Instant generatedAt,
        JsonNode contentSnapshot) {}
