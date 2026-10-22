package com.anjoguarda.imports;

import com.anjoguarda.audit.AuditLog;
import com.anjoguarda.audit.AuditLogRepository;
import com.anjoguarda.calendar.CalendarConfirmationRepository;
import com.anjoguarda.calendar.CalendarExceptionRepository;
import com.anjoguarda.calendar.CalendarExceptionService;
import com.anjoguarda.calendar.ConfirmationService;
import com.anjoguarda.calendar.ConfirmationStatus;
import com.anjoguarda.calendar.CreateCalendarExceptionRequest;
import com.anjoguarda.calendar.CreateConfirmationRequest;
import com.anjoguarda.family.ChildRepository;
import com.anjoguarda.family.FamilyAccessRepository;
import com.anjoguarda.storage.BlobStore;
import com.anjoguarda.storage.StorageKeys;
import com.anjoguarda.imports.ApproveImportRequest.ItemDecision;
import com.anjoguarda.notes.CreateNoteRequest;
import com.anjoguarda.notes.DailyNote;
import com.anjoguarda.notes.DailyNoteRepository;
import com.anjoguarda.notes.NoteCategory;
import com.anjoguarda.notes.NoteService;
import com.anjoguarda.notes.UpdateNoteRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImportService {

    static final long MAX_BYTES = 10L * 1024 * 1024;

    private final ChildRepository children;
    private final FamilyAccessRepository access;
    private final StoredImportRepository imports;
    private final ImportItemRepository items;
    private final BlobStore files;
    private final NoteService notes;
    private final DailyNoteRepository noteRows;
    private final ConfirmationService confirmations;
    private final CalendarConfirmationRepository confirmationRows;
    private final CalendarExceptionService exceptions;
    private final CalendarExceptionRepository exceptionRows;
    private final AuditLogRepository audits;
    private final ObjectMapper objectMapper;

    public ImportService(
            ChildRepository children,
            FamilyAccessRepository access,
            StoredImportRepository imports,
            ImportItemRepository items,
            @Qualifier("importStore") BlobStore files,
            NoteService notes,
            DailyNoteRepository noteRows,
            ConfirmationService confirmations,
            CalendarConfirmationRepository confirmationRows,
            CalendarExceptionService exceptions,
            CalendarExceptionRepository exceptionRows,
            AuditLogRepository audits,
            ObjectMapper objectMapper) {
        this.children = children;
        this.access = access;
        this.imports = imports;
        this.items = items;
        this.files = files;
        this.notes = notes;
        this.noteRows = noteRows;
        this.confirmations = confirmations;
        this.confirmationRows = confirmationRows;
        this.exceptions = exceptions;
        this.exceptionRows = exceptionRows;
        this.audits = audits;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportResponse start(UUID userId, UUID childId, MultipartFile file) {
        requireChild(userId, childId);
        String filename = filename(file == null ? null : file.getOriginalFilename());
        byte[] bytes = bytes(file);
        String sourceType = ImportText.sourceType(file.getContentType(), bytes);
        String extracted = ImportText.extract(sourceType, bytes);
        UUID id = UUID.randomUUID();
        String storageKey = StorageKeys.key(Instant.now(), id, extension(sourceType));
        files.write(storageKey, bytes);
        try {
            StoredImport stored = imports.save(new StoredImport(
                    id, childId, filename, storageKey, sourceType, extracted, Instant.now()));
            List<ImportItem> parsed = saveItems(stored.getId(), extracted);
            return toResponse(stored, parsed);
        } catch (RuntimeException error) {
            files.delete(storageKey);
            throw error;
        }
    }

    @Transactional(readOnly = true)
    public ImportResponse get(UUID userId, UUID importId) {
        StoredImport stored = requireImport(userId, importId);
        return toResponse(stored, items.findByImportIdOrderByPositionAsc(stored.getId()));
    }

    @Transactional
    public ImportResponse approve(UUID userId, UUID importId, ApproveImportRequest request) {
        StoredImport stored = requireImport(userId, importId);
        if ("REVISADO".equals(stored.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Importação já revisada");
        }
        List<ImportItem> parsed = items.findByImportIdOrderByPositionAsc(stored.getId());
        Map<UUID, ItemDecision> decisions = decisions(request, parsed);
        for (ImportItem item : parsed) {
            apply(userId, stored.getChildId(), item, decisions.get(item.getId()));
        }
        stored.markReviewed();
        audits.save(new AuditLog(
                UUID.randomUUID(),
                userId,
                "IMPORT_APPROVED",
                "import",
                stored.getId(),
                metadata(stored.getChildId())));
        return toResponse(stored, parsed);
    }

    private List<ImportItem> saveItems(UUID importId, String extracted) {
        List<ImportLines.Parsed> parsed = ImportLines.read(extracted);
        List<ImportItem> saved = new java.util.ArrayList<>();
        for (int position = 0; position < parsed.size(); position++) {
            ImportLines.Parsed line = parsed.get(position);
            saved.add(items.save(new ImportItem(
                    UUID.randomUUID(), importId, position, line.type(), json(line.data()))));
        }
        return saved;
    }

    private void apply(UUID userId, UUID childId, ImportItem item, ItemDecision decision) {
        JsonNode data = json(item.getProposedData());
        boolean conflict = conflicts(childId, item.getType(), date(data));
        String resolution = blank(decision.resolution());
        if (!decision.approved()) {
            if (resolution != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejeição não leva resolução");
            }
            item.reject();
            return;
        }
        if (conflict && resolution == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflito exige resolução");
        }
        if (!conflict && resolution != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolução sem conflito");
        }
        if ("MESCLAR".equals(resolution) && !"OBSERVACAO".equals(item.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mesclar só vale para observação");
        }
        if (resolution != null && !Set.of("MANTER", "SUBSTITUIR", "MESCLAR").contains(resolution)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolução inválida");
        }
        Result result = switch (item.getType()) {
            case "OBSERVACAO" -> observation(userId, childId, data, resolution);
            case "CONFIRMACAO" -> confirmation(userId, childId, data, resolution);
            case "EXCECAO" -> exception(userId, childId, data, resolution);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item inválido");
        };
        item.accept(resolution, result.type(), result.id());
    }

    private Result observation(UUID userId, UUID childId, JsonNode data, String resolution) {
        LocalDate date = date(data);
        DailyNote existing = noteRows.findByChildIdAndDateAndDeletedAtIsNull(childId, date).orElse(null);
        if ("MANTER".equals(resolution)) {
            return new Result("daily_note", required(existing).getId());
        }
        if ("MESCLAR".equals(resolution)) {
            DailyNote note = required(existing);
            return new Result("daily_note", notes.update(
                            userId,
                            note.getId(),
                            new UpdateNoteRequest(NoteCategory.valueOf(note.getCategory()), note.getText() + "\n" + text(data)))
                    .id());
        }
        if ("SUBSTITUIR".equals(resolution)) {
            return new Result("daily_note", notes.update(
                            userId,
                            required(existing).getId(),
                            new UpdateNoteRequest(NoteCategory.valueOf(textField(data, "category")), text(data)))
                    .id());
        }
        return new Result("daily_note", notes.create(
                        userId,
                        new CreateNoteRequest(childId, date, NoteCategory.valueOf(textField(data, "category")), text(data)))
                .id());
    }

    private Result confirmation(UUID userId, UUID childId, JsonNode data, String resolution) {
        LocalDate date = date(data);
        var existing = confirmationRows.findByChildIdAndDate(childId, date).orElse(null);
        if ("MANTER".equals(resolution)) {
            if (existing == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflito desatualizado");
            }
            return new Result("calendar_confirmation", existing.getId());
        }
        return new Result("calendar_confirmation", confirmations.upsert(userId, confirmationRequest(childId, data)).body().id());
    }

    private Result exception(UUID userId, UUID childId, JsonNode data, String resolution) {
        LocalDate date = date(data);
        var existing = exceptionRows.findByChildIdAndDate(childId, date).orElse(null);
        if ("MANTER".equals(resolution)) {
            if (existing == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflito desatualizado");
            }
            return new Result("calendar_exception", existing.getId());
        }
        return new Result(
                "calendar_exception",
                exceptions.upsert(
                                userId,
                                new CreateCalendarExceptionRequest(
                                        childId, date, uuid(data, "newGuardianId"), textField(data, "reason")))
                        .body()
                        .id());
    }

    private CreateConfirmationRequest confirmationRequest(UUID childId, JsonNode data) {
        String status = textField(data, "status");
        UUID guardian = data.hasNonNull("realizedGuardianId") ? uuid(data, "realizedGuardianId") : null;
        LocalTime start = data.hasNonNull("realizedStartTime") ? LocalTime.parse(data.get("realizedStartTime").asText()) : null;
        LocalTime end = data.hasNonNull("realizedEndTime") ? LocalTime.parse(data.get("realizedEndTime").asText()) : null;
        return new CreateConfirmationRequest(childId, date(data), ConfirmationStatus.valueOf(status), guardian, start, end, null);
    }

    private boolean conflicts(UUID childId, String type, LocalDate date) {
        return switch (type) {
            case "OBSERVACAO" -> noteRows.findByChildIdAndDateAndDeletedAtIsNull(childId, date).isPresent();
            case "CONFIRMACAO" -> confirmationRows.findByChildIdAndDate(childId, date).isPresent();
            case "EXCECAO" -> exceptionRows.findByChildIdAndDate(childId, date).isPresent();
            default -> false;
        };
    }

    private Map<UUID, ItemDecision> decisions(ApproveImportRequest request, List<ImportItem> parsed) {
        if (request == null || request.decisions() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decisões inválidas");
        }
        Map<UUID, ItemDecision> byId = new java.util.LinkedHashMap<>();
        for (ItemDecision decision : request.decisions()) {
            if (decision == null || decision.itemId() == null || byId.put(decision.itemId(), decision) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decisões inválidas");
            }
        }
        Set<UUID> expected = new HashSet<>();
        parsed.forEach(item -> expected.add(item.getId()));
        if (!expected.equals(byId.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decisões inválidas");
        }
        return byId;
    }

    private StoredImport requireImport(UUID userId, UUID importId) {
        StoredImport stored = imports.findById(importId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Importação não encontrada"));
        requireChild(userId, stored.getChildId());
        return stored;
    }

    private void requireChild(UUID userId, UUID childId) {
        children.findById(childId)
                .filter(child -> access.existsByFamilyIdAndUserId(child.getFamilyId(), userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criança não encontrada"));
    }

    private DailyNote required(DailyNote note) {
        if (note == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflito desatualizado");
        }
        return note;
    }

    private String filename(String original) {
        String name = original == null ? "" : original.replace('\\', '/').trim();
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1).trim();
        }
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.length() > 255 || name.indexOf('/') >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do arquivo inválido");
        }
        return name;
    }

    private byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
        try {
            byte[] content = file.getBytes();
            if (content.length == 0 || content.length > MAX_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
            }
            return content;
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
    }

    private String extension(String sourceType) {
        return "PDF".equals(sourceType) ? ".pdf" : ".docx";
    }

    private ImportResponse toResponse(StoredImport stored, List<ImportItem> parsed) {
        return new ImportResponse(
                stored.getId(),
                stored.getChildId(),
                stored.getFilename(),
                stored.getSourceType(),
                stored.getStatus(),
                stored.getExtractedText(),
                stored.getCreatedAt(),
                parsed.stream().map(item -> itemResponse(stored.getChildId(), item)).toList());
    }

    private ImportItemResponse itemResponse(UUID childId, ImportItem item) {
        JsonNode data = json(item.getProposedData());
        return new ImportItemResponse(
                item.getId(),
                item.getType(),
                data,
                item.getConfidence(),
                conflicts(childId, item.getType(), date(data)),
                item.getApproved(),
                item.getResolution(),
                item.getResultingEntityType(),
                item.getResultingEntityId());
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item inválido");
        }
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item inválido");
        }
    }

    private String metadata(UUID childId) {
        try {
            return objectMapper.writeValueAsString(Map.of("childId", childId.toString()));
        } catch (JsonProcessingException error) {
            return "{}";
        }
    }

    private LocalDate date(JsonNode data) {
        return LocalDate.parse(textField(data, "date"));
    }

    private String text(JsonNode data) {
        return textField(data, "text");
    }

    private String textField(JsonNode data, String field) {
        JsonNode value = data.get(field);
        if (value == null || value.asText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item inválido");
        }
        return value.asText();
    }

    private UUID uuid(JsonNode data, String field) {
        return UUID.fromString(textField(data, field));
    }

    private String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private record Result(String type, UUID id) {}
}
