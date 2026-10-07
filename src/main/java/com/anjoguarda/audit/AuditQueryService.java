package com.anjoguarda.audit;

import com.anjoguarda.calendar.BrazilTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuditQueryService {

    private final AuditLogRepository audits;

    public AuditQueryService(AuditLogRepository audits) {
        this.audits = audits;
    }

    @Transactional(readOnly = true)
    public List<AuditEntryResponse> list(UUID userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido");
        }
        Instant start = from.atStartOfDay(BrazilTime.ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(BrazilTime.ZONE).toInstant();
        return audits
                .findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(userId, start, end)
                .stream()
                .map(audit -> new AuditEntryResponse(
                        audit.getId(),
                        audit.getAction(),
                        audit.getEntityType(),
                        audit.getEntityId(),
                        audit.getCreatedAt()))
                .toList();
    }
}
