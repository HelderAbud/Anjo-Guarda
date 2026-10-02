package com.anjoguarda.calendar;

import com.anjoguarda.audit.AuditLog;
import com.anjoguarda.audit.AuditLogRepository;
import com.anjoguarda.family.Child;
import com.anjoguarda.family.ChildRepository;
import com.anjoguarda.family.FamilyAccessRepository;
import com.anjoguarda.family.Guardian;
import com.anjoguarda.family.GuardianRepository;
import com.anjoguarda.schedule.CustodyPlanRepository;
import com.anjoguarda.schedule.RuleType;
import com.anjoguarda.schedule.ScheduleRuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConfirmationService {

    private static final TypeReference<Map<String, UUID>> CONFIGURATION = new TypeReference<>() {};

    private final ChildRepository children;
    private final FamilyAccessRepository access;
    private final GuardianRepository guardians;
    private final CustodyPlanRepository plans;
    private final ScheduleRuleRepository rules;
    private final CalendarExceptionRepository exceptions;
    private final CalendarConfirmationRepository confirmations;
    private final AuditLogRepository audits;
    private final ObjectMapper objectMapper;
    private final CustodyDayCalculator calculator = new CustodyDayCalculator();

    public ConfirmationService(
            ChildRepository children,
            FamilyAccessRepository access,
            GuardianRepository guardians,
            CustodyPlanRepository plans,
            ScheduleRuleRepository rules,
            CalendarExceptionRepository exceptions,
            CalendarConfirmationRepository confirmations,
            AuditLogRepository audits,
            ObjectMapper objectMapper) {
        this.children = children;
        this.access = access;
        this.guardians = guardians;
        this.plans = plans;
        this.rules = rules;
        this.exceptions = exceptions;
        this.confirmations = confirmations;
        this.audits = audits;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UpsertResult upsert(UUID userId, CreateConfirmationRequest request) {
        requireChild(userId, request.childId());
        if (request.realizedEndTime() != null
                && request.realizedStartTime() != null
                && request.realizedEndTime().isBefore(request.realizedStartTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Horário final anterior ao inicial");
        }

        UUID planned = plannedGuardian(request.childId(), request.date());
        Resolved resolved = resolve(request, planned);
        Optional<CalendarConfirmation> existing =
                confirmations.findByChildIdAndDate(request.childId(), request.date());
        CalendarConfirmation saved;
        boolean created;
        if (existing.isPresent()) {
            saved = existing.get();
            saved.update(
                    request.status(),
                    resolved.realizedGuardianId(),
                    resolved.startTime(),
                    resolved.endTime(),
                    request.note());
            created = false;
        } else {
            saved = confirmations.save(new CalendarConfirmation(
                    UUID.randomUUID(),
                    request.childId(),
                    request.date(),
                    request.status(),
                    resolved.realizedGuardianId(),
                    resolved.startTime(),
                    resolved.endTime(),
                    request.note(),
                    userId));
            created = true;
        }
        audits.save(new AuditLog(
                UUID.randomUUID(),
                userId,
                "CONFIRMATION_UPSERT",
                "calendar_confirmation",
                saved.getId(),
                metadata(saved)));
        return new UpsertResult(toResponse(saved, planned), created);
    }

    private Resolved resolve(CreateConfirmationRequest request, UUID planned) {
        return switch (request.status()) {
            case REALIZADO -> realizado(request, planned);
            case ALTERADO -> alterado(request, planned);
            case NAO_REALIZADO -> naoRealizado(request);
        };
    }

    private Resolved realizado(CreateConfirmationRequest request, UUID planned) {
        if (planned == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dia sem planejamento");
        }
        if (request.realizedGuardianId() != null && !request.realizedGuardianId().equals(planned)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Realizado precisa coincidir com o planejado");
        }
        if (request.realizedStartTime() != null || request.realizedEndTime() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Horário diferente é alteração");
        }
        return new Resolved(planned, null, null);
    }

    private Resolved alterado(CreateConfirmationRequest request, UUID planned) {
        if (request.realizedGuardianId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o responsável realizado");
        }
        Guardian guardian = guardians
                .findById(request.realizedGuardianId())
                .filter(found -> found.getChildId().equals(request.childId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Responsável inválido"));
        boolean sameGuardian = planned != null && guardian.getId().equals(planned);
        boolean noTimes = request.realizedStartTime() == null && request.realizedEndTime() == null;
        if (sameGuardian && noTimes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aconteceu como planejado");
        }
        return new Resolved(guardian.getId(), request.realizedStartTime(), request.realizedEndTime());
    }

    private Resolved naoRealizado(CreateConfirmationRequest request) {
        if (request.realizedGuardianId() != null
                || request.realizedStartTime() != null
                || request.realizedEndTime() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não realizado não tem responsável realizado");
        }
        return new Resolved(null, null, null);
    }

    private UUID plannedGuardian(UUID childId, LocalDate date) {
        UUID fromRule = plans.findByChildIdAndActiveTrue(childId)
                .map(plan -> rules.findByPlanId(plan.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano não encontrado")))
                .map(rule -> calculator.guardianOn(
                        date,
                        rule.getStartDate(),
                        rule.getEndDate(),
                        RuleType.valueOf(rule.getRuleType()),
                        readConfiguration(rule.getConfigurationJson())))
                .orElse(null);
        return exceptions.findByChildIdAndDate(childId, date)
                .map(CalendarException::getNewGuardianId)
                .orElse(fromRule);
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

    private String metadata(CalendarConfirmation confirmation) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "childId", confirmation.getChildId().toString(),
                    "date", confirmation.getDate().toString(),
                    "status", confirmation.getStatus()));
        } catch (JsonProcessingException error) {
            return "{}";
        }
    }

    private ConfirmationResponse toResponse(CalendarConfirmation confirmation, UUID planned) {
        return new ConfirmationResponse(
                confirmation.getId(),
                confirmation.getChildId(),
                confirmation.getDate(),
                confirmation.getStatus(),
                planned,
                confirmation.getRealizedGuardianId(),
                confirmation.getRealizedStartTime(),
                confirmation.getRealizedEndTime(),
                confirmation.getNote());
    }

    public record UpsertResult(ConfirmationResponse body, boolean created) {}

    private record Resolved(UUID realizedGuardianId, LocalTime startTime, LocalTime endTime) {}
}
