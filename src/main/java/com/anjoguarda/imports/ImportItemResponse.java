package com.anjoguarda.imports;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record ImportItemResponse(
        UUID id,
        String type,
        JsonNode proposedData,
        int confidence,
        boolean conflict,
        Boolean approved,
        String resolution,
        String resultingEntityType,
        UUID resultingEntityId) {}
