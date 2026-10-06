package com.anjoguarda.notes;

import com.anjoguarda.audit.AuditLog;
import com.anjoguarda.audit.AuditLogRepository;
import com.anjoguarda.family.Child;
import com.anjoguarda.family.ChildRepository;
import com.anjoguarda.family.FamilyAccessRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NoteService {

    private final ChildRepository children;
    private final FamilyAccessRepository access;
    private final DailyNoteRepository notes;
    private final AuditLogRepository audits;
    private final ObjectMapper objectMapper;

    public NoteService(
            ChildRepository children,
            FamilyAccessRepository access,
            DailyNoteRepository notes,
            AuditLogRepository audits,
            ObjectMapper objectMapper) {
        this.children = children;
        this.access = access;
        this.notes = notes;
        this.audits = audits;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NoteResponse create(UUID userId, CreateNoteRequest request) {
        requireChild(userId, request.childId());
        if (notes.findByChildIdAndDateAndDeletedAtIsNull(request.childId(), request.date()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma observação neste dia");
        }
        DailyNote saved = notes.save(new DailyNote(
                UUID.randomUUID(),
                request.childId(),
                request.date(),
                request.category().name(),
                request.text().trim(),
                userId,
                Instant.now()));
        audit(userId, "NOTE_CREATED", saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> list(
            UUID userId, UUID childId, LocalDate from, LocalDate to, String query, NoteCategory category) {
        requireChild(userId, childId);
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido");
        }
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String categoryName = category == null ? null : category.name();
        return notes.findByChildIdAndDeletedAtIsNullAndDateBetweenOrderByDateAsc(childId, from, to).stream()
                .filter(note -> categoryName == null || categoryName.equals(note.getCategory()))
                .filter(note -> term.isEmpty() || note.getText().toLowerCase(Locale.ROOT).contains(term))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NoteResponse update(UUID userId, UUID noteId, UpdateNoteRequest request) {
        DailyNote note = activeNote(userId, noteId);
        note.revise(request.category().name(), request.text().trim(), Instant.now());
        audit(userId, "NOTE_UPDATED", note);
        return toResponse(note);
    }

    @Transactional
    public void delete(UUID userId, UUID noteId) {
        DailyNote note = activeNote(userId, noteId);
        note.delete(Instant.now());
        audit(userId, "NOTE_DELETED", note);
    }

    private DailyNote activeNote(UUID userId, UUID noteId) {
        DailyNote note = notes.findByIdAndDeletedAtIsNull(noteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Observação não encontrada"));
        requireChild(userId, note.getChildId());
        return note;
    }

    private Child requireChild(UUID userId, UUID childId) {
        return children.findById(childId)
                .filter(child -> access.existsByFamilyIdAndUserId(child.getFamilyId(), userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criança não encontrada"));
    }

    private void audit(UUID userId, String action, DailyNote note) {
        audits.save(new AuditLog(
                UUID.randomUUID(), userId, action, "daily_note", note.getId(), metadata(note)));
    }

    private String metadata(DailyNote note) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "childId", note.getChildId().toString(),
                    "date", note.getDate().toString(),
                    "category", note.getCategory()));
        } catch (JsonProcessingException error) {
            return "{}";
        }
    }

    private NoteResponse toResponse(DailyNote note) {
        return new NoteResponse(
                note.getId(),
                note.getChildId(),
                note.getDate(),
                note.getCategory(),
                note.getText(),
                note.getCreatedAt(),
                note.getUpdatedAt());
    }
}
