package com.anjoguarda.report;

import java.time.Instant;
import java.util.UUID;

public record MonthlyReportVersionResponse(UUID id, int version, Instant generatedAt) {}
