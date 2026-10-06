package com.anjoguarda.notes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateNoteRequest(@NotNull NoteCategory category, @NotBlank String text) {}
