package com.anjoguarda.notes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NoteResponse(
        UUID id,
        UUID childId,
        LocalDate date,
        String category,
        String text,
        Instant createdAt,
        Instant updatedAt) {}
