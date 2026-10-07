package com.anjoguarda.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id, String action, String entityType, UUID entityId, Instant createdAt) {}
