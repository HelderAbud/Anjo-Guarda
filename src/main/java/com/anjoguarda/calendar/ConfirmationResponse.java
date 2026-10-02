package com.anjoguarda.calendar;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ConfirmationResponse(
        UUID id,
        UUID childId,
        LocalDate date,
        String status,
        UUID plannedGuardianId,
        UUID realizedGuardianId,
        LocalTime realizedStartTime,
        LocalTime realizedEndTime,
        String note) {}
