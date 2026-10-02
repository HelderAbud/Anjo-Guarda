package com.anjoguarda.calendar;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateConfirmationRequest(
        @NotNull UUID childId,
        @NotNull LocalDate date,
        @NotNull ConfirmationStatus status,
        UUID realizedGuardianId,
        LocalTime realizedStartTime,
        LocalTime realizedEndTime,
        @Size(max = 500) String note) {}
