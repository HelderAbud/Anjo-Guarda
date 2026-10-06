package com.anjoguarda.notes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateNoteRequest(
        @NotNull UUID childId, @NotNull LocalDate date, @NotNull NoteCategory category, @NotBlank String text) {}
