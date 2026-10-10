package com.anjoguarda.imports;

import java.time.Instant;
import java.util.UUID;

public record ImportResponse(
        UUID id,
        UUID childId,
        String filename,
        String sourceType,
        String status,
        String extractedText,
        Instant createdAt) {}
