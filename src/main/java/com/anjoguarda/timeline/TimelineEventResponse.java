package com.anjoguarda.timeline;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TimelineEventResponse(
        LocalDate date,
        String type,
        Instant occurredAt,
        UUID entityId,
        String action,
        UUID newGuardianId,
        String status,
        String category,
        String text) {}
