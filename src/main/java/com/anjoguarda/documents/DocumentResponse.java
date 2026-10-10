package com.anjoguarda.documents;

import java.util.UUID;

public record DocumentResponse(
        UUID id, UUID childId, String category, String filename, String mimeType, long size) {}
