package com.anjoguarda.family;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ChildResponse(UUID id, String name, LocalDate birthDate, List<GuardianResponse> guardians) {

    public record GuardianResponse(UUID id, String name, String relationship, UUID userId) {}
}
