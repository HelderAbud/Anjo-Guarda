package com.anjoguarda.report;

import com.anjoguarda.audit.AuditLog;
import com.anjoguarda.audit.AuditLogRepository;
import com.anjoguarda.calendar.CalendarConfirmation;
import com.anjoguarda.calendar.CalendarConfirmationRepository;
import com.anjoguarda.calendar.MonthCalendarResponse;
import com.anjoguarda.calendar.MonthCalendarService;
import com.anjoguarda.family.Child;
import com.anjoguarda.family.ChildRepository;
import com.anjoguarda.family.FamilyAccessRepository;
import com.anjoguarda.notes.DailyNote;
import com.anjoguarda.notes.DailyNoteRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MonthlyReportService {

    private final ChildRepository children;
    private final FamilyAccessRepository access;
    private final MonthCalendarService calendars;
    private final CalendarConfirmationRepository confirmations;
    private final DailyNoteRepository notes;
    private final MonthlyReportRepository reports;
    private final AuditLogRepository audits;
    private final ObjectMapper objectMapper;

    public MonthlyReportService(
            ChildRepository children,
            FamilyAccessRepository access,
            MonthCalendarService calendars,
            CalendarConfirmationRepository confirmations,
            DailyNoteRepository notes,
            MonthlyReportRepository reports,
            AuditLogRepository audits,
            ObjectMapper objectMapper) {
        this.children = children;
        this.access = access;
        this.calendars = calendars;
        this.confirmations = confirmations;
        this.notes = notes;
        this.reports = reports;
        this.audits = audits;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MonthlyReportResponse generate(UUID userId, UUID childId, int year, int month) {
        Child child = requireChild(userId, childId);
        yearMonth(year, month);
        MonthCalendarResponse calendar = calendars.month(userId, childId, year, month);
        int version = reports.findFirstByChildIdAndYearAndMonthOrderByVersionDesc(childId, year, month)
                .map(existing -> existing.getVersion() + 1)
                .orElse(1);
        Instant generatedAt = Instant.now();
        String snapshot = writeSnapshot(child, calendar);
        MonthlyReport saved = reports.save(new MonthlyReport(
                UUID.randomUUID(), childId, year, month, version, generatedAt, snapshot));
        audits.save(new AuditLog(
                UUID.randomUUID(),
                userId,
                "REPORT_CREATED",
                "monthly_report",
                saved.getId(),
                metadata(saved)));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MonthlyReportVersionResponse> list(UUID userId, UUID childId, int year, int month) {
        requireChild(userId, childId);
        yearMonth(year, month);
        return reports.findByChildIdAndYearAndMonthOrderByVersionAsc(childId, year, month).stream()
                .map(report -> new MonthlyReportVersionResponse(
                        report.getId(), report.getVersion(), report.getGeneratedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MonthlyReportResponse get(UUID userId, UUID reportId) {
        MonthlyReport report = reports.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relatório não encontrado"));
        requireChild(userId, report.getChildId());
        return toResponse(report);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(UUID userId, UUID reportId) {
        MonthlyReportResponse report = get(userId, reportId);
        return MonthlyReportPdf.render(report.version(), report.contentSnapshot());
    }

    private String writeSnapshot(Child child, MonthCalendarResponse calendar) {
        LocalDate start = LocalDate.of(calendar.year(), calendar.month(), 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        Map<LocalDate, CalendarConfirmation> confirmed = new LinkedHashMap<>();
        for (CalendarConfirmation confirmation : confirmations.findByChildIdAndDateBetween(child.getId(), start, end)) {
            confirmed.put(confirmation.getDate(), confirmation);
        }
        Map<LocalDate, DailyNote> noted = new LinkedHashMap<>();
        for (DailyNote note : notes.findByChildIdAndDeletedAtIsNullAndDateBetweenOrderByDateAsc(child.getId(), start, end)) {
            noted.put(note.getDate(), note);
        }
        List<Map<String, Object>> days = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("PLANEJADO", 0);
        counts.put("REALIZADO", 0);
        counts.put("ALTERADO", 0);
        counts.put("NAO_REALIZADO", 0);
        for (MonthCalendarResponse.DayResponse day : calendar.days()) {
            CalendarConfirmation confirmation = confirmed.get(day.date());
            DailyNote note = noted.get(day.date());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day.date());
            row.put("weekday", day.weekday());
            row.put("guardianId", day.guardianId());
            row.put("status", day.status());
            row.put("exception", day.exception());
            row.put("realizedGuardianId", day.realizedGuardianId());
            row.put("realizedStartTime", confirmation == null ? null : confirmation.getRealizedStartTime());
            row.put("realizedEndTime", confirmation == null ? null : confirmation.getRealizedEndTime());
            row.put("observation", note == null ? null : Map.of("category", note.getCategory(), "text", note.getText()));
            days.add(row);
            counts.merge(day.status(), 1, Integer::sum);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("childId", child.getId());
        snapshot.put("childName", child.getName());
        snapshot.put("year", calendar.year());
        snapshot.put("month", calendar.month());
        snapshot.put("counts", counts);
        snapshot.put("days", days);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resumo inválido");
        }
    }

    private MonthlyReportResponse toResponse(MonthlyReport report) {
        try {
            JsonNode snapshot = objectMapper.readTree(report.getContentSnapshot());
            return new MonthlyReportResponse(
                    report.getId(),
                    report.getChildId(),
                    report.getYear(),
                    report.getMonth(),
                    report.getVersion(),
                    report.getGeneratedAt(),
                    snapshot);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resumo inválido");
        }
    }

    private String metadata(MonthlyReport report) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "childId", report.getChildId().toString(),
                    "year", report.getYear(),
                    "month", report.getMonth(),
                    "version", report.getVersion()));
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private Child requireChild(UUID userId, UUID childId) {
        return children.findById(childId)
                .filter(child -> access.existsByFamilyIdAndUserId(child.getFamilyId(), userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criança não encontrada"));
    }

    private YearMonth yearMonth(int year, int month) {
        try {
            return YearMonth.of(year, month);
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mês inválido");
        }
    }
}
