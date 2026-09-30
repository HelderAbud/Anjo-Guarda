package com.anjoguarda.schedule;

import com.anjoguarda.family.Child;
import com.anjoguarda.family.ChildRepository;
import com.anjoguarda.family.FamilyAccessRepository;
import com.anjoguarda.family.Guardian;
import com.anjoguarda.family.GuardianRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustodyPlanService {

    private static final TypeReference<Map<String, UUID>> CONFIGURATION = new TypeReference<>() {};

    private final ChildRepository children;
    private final FamilyAccessRepository access;
    private final GuardianRepository guardians;
    private final CustodyPlanRepository plans;
    private final ScheduleRuleRepository rules;
    private final ObjectMapper objectMapper;

    public CustodyPlanService(
            ChildRepository children,
            FamilyAccessRepository access,
            GuardianRepository guardians,
            CustodyPlanRepository plans,
            ScheduleRuleRepository rules,
            ObjectMapper objectMapper) {
        this.children = children;
        this.access = access;
        this.guardians = guardians;
        this.plans = plans;
        this.rules = rules;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CustodyPlanResponse save(UUID userId, UUID childId, SaveCustodyPlanRequest request) {
        requireChild(userId, childId);
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data final anterior à inicial");
        }
        if (!request.configuration().keySet().equals(request.ruleType().keys())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuração da regra inválida");
        }
        Set<UUID> allowed = guardians.findByChildId(childId).stream()
                .map(Guardian::getId)
                .collect(Collectors.toSet());
        if (!allowed.containsAll(request.configuration().values())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Responsável não pertence à criança");
        }
        if (plans.existsByChildIdAndActiveTrue(childId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um plano ativo");
        }

        CustodyPlan plan = plans.save(new CustodyPlan(
                UUID.randomUUID(), childId, request.name(), request.startDate(), request.endDate()));
        ScheduleRule rule = rules.save(new ScheduleRule(
                UUID.randomUUID(),
                plan.getId(),
                request.ruleType(),
                toJson(request.configuration()),
                request.startDate(),
                request.endDate()));
        return toResponse(plan, rule);
    }

    @Transactional(readOnly = true)
    public CustodyPlanResponse get(UUID userId, UUID childId) {
        requireChild(userId, childId);
        CustodyPlan plan = plans.findByChildIdAndActiveTrue(childId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano não encontrado"));
        ScheduleRule rule = rules.findByPlanId(plan.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano não encontrado"));
        return toResponse(plan, rule);
    }

    private Child requireChild(UUID userId, UUID childId) {
        return children.findById(childId)
                .filter(child -> access.existsByFamilyIdAndUserId(child.getFamilyId(), userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criança não encontrada"));
    }

    private CustodyPlanResponse toResponse(CustodyPlan plan, ScheduleRule rule) {
        return new CustodyPlanResponse(
                plan.getId(),
                plan.getChildId(),
                plan.getName(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.isActive(),
                new CustodyPlanResponse.RuleResponse(
                        rule.getId(),
                        rule.getRuleType(),
                        rule.getStartDate(),
                        rule.getEndDate(),
                        readConfiguration(rule.getConfigurationJson())));
    }

    private String toJson(Map<String, UUID> configuration) {
        try {
            return objectMapper.writeValueAsString(configuration);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuração da regra inválida");
        }
    }

    private Map<String, UUID> readConfiguration(String json) {
        try {
            return objectMapper.readValue(json, CONFIGURATION);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuração da regra inválida");
        }
    }
}
