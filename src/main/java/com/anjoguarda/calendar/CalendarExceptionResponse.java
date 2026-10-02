package com.anjoguarda.calendar;

import java.time.LocalDate;
import java.util.UUID;

public record CalendarExceptionResponse(
        UUID id,
        UUID childId,
        LocalDate date,
        String reason,
        UUID originalGuardianId,
        UUID newGuardianId,
        UUID createdBy) {}
