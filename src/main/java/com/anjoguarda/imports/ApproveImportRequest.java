package com.anjoguarda.imports;

import java.util.List;
import java.util.UUID;

public record ApproveImportRequest(List<ItemDecision> decisions) {

    public record ItemDecision(UUID itemId, boolean approved, String resolution) {}
}
