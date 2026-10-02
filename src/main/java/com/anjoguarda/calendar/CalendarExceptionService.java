package com.anjoguarda.calendar;

import com.anjoguarda.audit.AuditLog;
import com.anjoguarda.audit.AuditLogRepository;
import com.anjoguarda.family.Child;
import com.anjoguarda.family.ChildRepository;
import com.anjoguarda.family.FamilyAccessRepository;
import com.anjoguarda.family.Guardian;
import com.anjoguarda.family.GuardianRepository;
import com.anjoguarda.schedule.CustodyPlan;
import com.anjoguarda.schedule.CustodyPlanRepository;
import com.anjoguarda.schedule.RuleType;
import com.anjoguarda.schedule.ScheduleRule;
import com.anjoguarda.schedule.ScheduleRuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CalendarExceptionService {

    private static final TypeReference<Map<String, UUID>> CONFIGURATION = new TypeReference<>() {};

    private final ChildRepository children;
    private final FamilyAccessRepository access;
    private final GuardianRepository guardians;
    private final CustodyPlanRepository plans;
    private final ScheduleRuleRepository rules;
    private final CalendarExceptionRepository exceptions;
    private final AuditLogRepository audits;
    private final ObjectMapper objectMapper;
    private final CustodyDayCalculator calculator = new CustodyDayCalculator();

    public CalendarExceptionService(
            ChildRepository children,
            FamilyAccessRepository access,
            GuardianRepository guardians,
            CustodyPlanRepository plans,
            ScheduleRuleRepository rules,
            CalendarExceptionRepository exceptions,
            AuditLogRepository audits,
            ObjectMapper objectMapper) {
        this.children = children;
        this.access = access;
        this.guardians = guardians;
        this.plans = plans;
        this.rules = rules;
        this.exceptions = exceptions;
        this.audits = audits;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UpsertResult upsert(UUID userId, CreateCalendarExceptionRequest request) {
        requireChild(userId, request.childId());
        Guardian newGuardian = guardians
                .findById(request.newGuardianId())
                .filter(guardian -> guardian.getChildId().equals(request.childId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Responsável inválido"));

        UUID originalGuardianId = plannedGuardian(request.childId(), request.date());
        Optional<CalendarException> existing = exceptions.findByChildIdAndDate(request.childId(), request.date());
        CalendarException saved;
        boolean created;
        if (existing.isPresent()) {
            saved = existing.get();
            saved.update(request.reason(), originalGuardianId, newGuardian.getId());
            created = false;
        } else {
            saved = new CalendarException(
                    UUID.randomUUID(),
                    request.childId(),
                    request.date(),
                    request.reason(),
                    originalGuardianId,
                    newGuardian.getId(),
                    userId);
            exceptions.save(saved);
            created = true;
        }
        audits.save(new AuditLog(
                UUID.randomUUID(),
                userId,
                "EXCEPTION_UPSERT",
                "calendar_exception",
                saved.getId(),
                metadata(saved)));
        return new UpsertResult(toResponse(saved), created);
    }

    private UUID plannedGuardian(UUID childId, java.time.LocalDate date) {
        return plans.findByChildIdAndActiveTrue(childId)
                .map(this::ruleOf)
                .map(rule -> calculator.guardianOn(date, rule.start(), rule.end(), rule.type(), rule.configuration()))
                .orElse(null);
    }

    private ActiveRule ruleOf(CustodyPlan plan) {
        ScheduleRule rule = rules.findByPlanId(plan.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano não encontrado"));
        return new ActiveRule(
                rule.getStartDate(),
                rule.getEndDate(),
                RuleType.valueOf(rule.getRuleType()),
                readConfiguration(rule.getConfigurationJson()));
    }

    private Child requireChild(UUID userId, UUID childId) {
        return children.findById(childId)
                .filter(child -> access.existsByFamilyIdAndUserId(child.getFamilyId(), userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criança não encontrada"));
    }

    private Map<String, UUID> readConfiguration(String json) {
        try {
            return objectMapper.readValue(json, CONFIGURATION);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuração da regra inválida");
        }
    }

    private String metadata(CalendarException exception) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "childId", exception.getChildId().toString(),
                    "date", exception.getDate().toString(),
                    "newGuardianId", exception.getNewGuardianId().toString()));
        } catch (JsonProcessingException error) {
            return "{}";
        }
    }

    private CalendarExceptionResponse toResponse(CalendarException exception) {
        return new CalendarExceptionResponse(
                exception.getId(),
                exception.getChildId(),
                exception.getDate(),
                exception.getReason(),
                exception.getOriginalGuardianId(),
                exception.getNewGuardianId(),
                exception.getCreatedBy());
    }

    public record UpsertResult(CalendarExceptionResponse body, boolean created) {}

    private record ActiveRule(
            java.time.LocalDate start,
            java.time.LocalDate end,
            RuleType type,
            Map<String, UUID> configuration) {}
}
