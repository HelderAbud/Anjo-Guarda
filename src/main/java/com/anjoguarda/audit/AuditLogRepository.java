package com.anjoguarda.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            UUID userId, Instant start, Instant end);

    @Query(
            value = """
                    SELECT * FROM audit_logs
                    WHERE metadata->>'childId' = :childId
                      AND metadata->>'date' >= :from
                      AND metadata->>'date' <= :to
                      AND action IN (
                        'EXCEPTION_UPSERT',
                        'CONFIRMATION_UPSERT',
                        'NOTE_CREATED',
                        'NOTE_UPDATED',
                        'NOTE_DELETED')
                    """,
            nativeQuery = true)
    List<AuditLog> findForChildBetween(
            @Param("childId") String childId, @Param("from") String from, @Param("to") String to);
}
