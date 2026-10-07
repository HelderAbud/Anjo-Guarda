package com.anjoguarda.timeline;

import com.anjoguarda.audit.AuditLog;
import com.anjoguarda.audit.AuditLogRepository;
import com.anjoguarda.calendar.CalendarConfirmation;
import com.anjoguarda.calendar.CalendarConfirmationRepository;
import com.anjoguarda.calendar.CalendarException;
import com.anjoguarda.calendar.CalendarExceptionRepository;
import com.anjoguarda.family.ChildRepository;
import com.anjoguarda.family.FamilyAccessRepository;
import com.anjoguarda.notes.DailyNote;
import com.anjoguarda.notes.DailyNoteRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TimelineService {

    private static final TypeReference<Map<String, String>> METADATA = new TypeReference<>() {};

    private final ChildRepository children;
    private final FamilyAccessRepository access;
    private final CalendarExceptionRepository exceptions;
    private final CalendarConfirmationRepository confirmations;
    private final DailyNoteRepository notes;
    private final AuditLogRepository audits;
    private final ObjectMapper objectMapper;

    public TimelineService(
            ChildRepository children,
            FamilyAccessRepository access,
            CalendarExceptionRepository exceptions,
            CalendarConfirmationRepository confirmations,
            DailyNoteRepository notes,
            AuditLogRepository audits,
            ObjectMapper objectMapper) {
        this.children = children;
        this.access = access;
        this.exceptions = exceptions;
        this.confirmations = confirmations;
        this.notes = notes;
        this.audits = audits;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public TimelineResponse list(
            UUID userId, UUID childId, LocalDate from, LocalDate to, TimelineEventType type) {
        requireChild(userId, childId);
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido");
        }
        List<TimelineEventResponse> events = new ArrayList<>();
        if (type == null || type == TimelineEventType.EXCECAO) {
            for (CalendarException exception : exceptions.findByChildIdAndDateBetween(childId, from, to)) {
                events.add(new TimelineEventResponse(
                        exception.getDate(),
                        TimelineEventType.EXCECAO.name(),
                        exception.getCreatedAt(),
                        exception.getId(),
                        null,
                        exception.getNewGuardianId(),
                        null,
                        null,
                        null));
            }
        }
        if (type == null || type == TimelineEventType.CONFIRMACAO) {
            for (CalendarConfirmation confirmation : confirmations.findByChildIdAndDateBetween(childId, from, to)) {
                events.add(new TimelineEventResponse(
                        confirmation.getDate(),
                        TimelineEventType.CONFIRMACAO.name(),
                        confirmation.getCreatedAt(),
                        confirmation.getId(),
                        null,
                        null,
                        confirmation.getStatus(),
                        null,
                        null));
            }
        }
        if (type == null || type == TimelineEventType.OBSERVACAO) {
            for (DailyNote note : notes.findByChildIdAndDeletedAtIsNullAndDateBetweenOrderByDateAsc(childId, from, to)) {
                events.add(new TimelineEventResponse(
                        note.getDate(),
                        TimelineEventType.OBSERVACAO.name(),
                        note.getUpdatedAt(),
                        note.getId(),
                        null,
                        null,
                        null,
                        note.getCategory(),
                        note.getText()));
            }
        }
        if (type == null || type == TimelineEventType.AUDITORIA) {
            for (AuditLog audit : audits.findForChildBetween(childId.toString(), from.toString(), to.toString())) {
                events.add(new TimelineEventResponse(
                        LocalDate.parse(readDate(audit)),
                        TimelineEventType.AUDITORIA.name(),
                        audit.getCreatedAt(),
                        audit.getEntityId(),
                        audit.getAction(),
                        null,
                        null,
                        null,
                        null));
            }
        }
        events.sort(Comparator.comparing(TimelineEventResponse::date)
                .thenComparing(TimelineEventResponse::occurredAt)
                .thenComparing(TimelineEventResponse::type));
        return new TimelineResponse(childId, from, to, events);
    }

    private String readDate(AuditLog audit) {
        try {
            String date = objectMapper.readValue(audit.getMetadata(), METADATA).get("date");
            if (date == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auditoria sem data");
            }
            return date;
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auditoria sem data");
        }
    }

    private void requireChild(UUID userId, UUID childId) {
        children.findById(childId)
                .filter(child -> access.existsByFamilyIdAndUserId(child.getFamilyId(), userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criança não encontrada"));
    }
}
