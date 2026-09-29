package com.anjoguarda.family;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDate;
import java.util.List;

public record RegisterChildRequest(
        @NotBlank String name,
        LocalDate birthDate,
        @NotEmpty List<@Valid GuardianRequest> guardians) {

    public record GuardianRequest(@NotBlank String name, @NotBlank String relationship) {}
}
