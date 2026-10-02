package com.anjoguarda.calendar;

import com.anjoguarda.family.Child;
import com.anjoguarda.family.ChildRepository;
import com.anjoguarda.family.FamilyAccessRepository;
import com.anjoguarda.schedule.CustodyPlan;
import com.anjoguarda.schedule.CustodyPlanRepository;
import com.anjoguarda.schedule.RuleType;
import com.anjoguarda.schedule.ScheduleRule;
import com.anjoguarda.schedule.ScheduleRuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MonthCalendarService {

    private static final TypeReference<Map<String, UUID>> CONFIGURATION = new TypeReference<>() {};

    private final ChildRepository children;
    private final FamilyAccessRepository access;
    private final CustodyPlanRepository plans;
    private final ScheduleRuleRepository rules;
    private final CalendarExceptionRepository exceptions;
    private final ObjectMapper objectMapper;
    private final CustodyDayCalculator calculator = new CustodyDayCalculator();

    public MonthCalendarService(
            ChildRepository children,
            FamilyAccessRepository access,
            CustodyPlanRepository plans,
            ScheduleRuleRepository rules,
            CalendarExceptionRepository exceptions,
            ObjectMapper objectMapper) {
        this.children = children;
        this.access = access;
        this.plans = plans;
        this.rules = rules;
        this.exceptions = exceptions;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public MonthCalendarResponse month(UUID userId, UUID childId, int year, int month) {
        requireChild(userId, childId);
        YearMonth yearMonth = yearMonth(year, month);
        ActiveRule rule = activeRule(childId);
        LocalDate date = yearMonth.atDay(1).atStartOfDay(BrazilTime.ZONE).toLocalDate();
        LocalDate limit = yearMonth.plusMonths(1).atDay(1).atStartOfDay(BrazilTime.ZONE).toLocalDate();
        Map<LocalDate, UUID> overrides = exceptionsByDate(childId, date, limit.minusDays(1));
        List<MonthCalendarResponse.DayResponse> days = new ArrayList<>();
        while (date.isBefore(limit)) {
            UUID planned = rule == null
                    ? null
                    : calculator.guardianOn(date, rule.start(), rule.end(), rule.type(), rule.configuration());
            boolean hasException = overrides.containsKey(date);
            UUID guardianId = hasException ? overrides.get(date) : planned;
            days.add(new MonthCalendarResponse.DayResponse(
                    date, date.getDayOfWeek().name(), guardianId, hasException));
            date = date.plusDays(1);
        }
        return new MonthCalendarResponse(childId, year, month, BrazilTime.ZONE.getId(), days);
    }

    private Map<LocalDate, UUID> exceptionsByDate(UUID childId, LocalDate start, LocalDate end) {
        Map<LocalDate, UUID> overrides = new HashMap<>();
        for (CalendarException exception : exceptions.findByChildIdAndDateBetween(childId, start, end)) {
            overrides.put(exception.getDate(), exception.getNewGuardianId());
        }
        return overrides;
    }

    private YearMonth yearMonth(int year, int month) {
        try {
            return YearMonth.of(year, month);
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mês inválido");
        }
    }

    private ActiveRule activeRule(UUID childId) {
        return plans.findByChildIdAndActiveTrue(childId).map(this::ruleOf).orElse(null);
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

    private record ActiveRule(LocalDate start, LocalDate end, RuleType type, Map<String, UUID> configuration) {}
}
