package com.anjoguarda.calendar;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateCalendarExceptionRequest(
        @NotNull UUID childId,
        @NotNull LocalDate date,
        @NotNull UUID newGuardianId,
        String reason) {}
