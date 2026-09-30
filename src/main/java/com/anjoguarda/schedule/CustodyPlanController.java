package com.anjoguarda.schedule;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/children/{childId}/custody-plan")
public class CustodyPlanController {

    private final CustodyPlanService custodyPlanService;

    public CustodyPlanController(CustodyPlanService custodyPlanService) {
        this.custodyPlanService = custodyPlanService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustodyPlanResponse save(@PathVariable UUID childId, @Valid @RequestBody SaveCustodyPlanRequest request) {
        return custodyPlanService.save(currentUserId(), childId, request);
    }

    @GetMapping
    public CustodyPlanResponse get(@PathVariable UUID childId) {
        return custodyPlanService.get(currentUserId(), childId);
    }

    private UUID currentUserId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
