package com.anjoguarda.report;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/monthly")
public class MonthlyReportController {

    private final MonthlyReportService monthlyReportService;

    public MonthlyReportController(MonthlyReportService monthlyReportService) {
        this.monthlyReportService = monthlyReportService;
    }

    @PostMapping
    public ResponseEntity<MonthlyReportResponse> generate(
            @RequestParam UUID childId, @RequestParam int year, @RequestParam int month) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(monthlyReportService.generate(currentUserId(), childId, year, month));
    }

    @GetMapping
    public List<MonthlyReportVersionResponse> list(
            @RequestParam UUID childId, @RequestParam int year, @RequestParam int month) {
        return monthlyReportService.list(currentUserId(), childId, year, month);
    }

    @GetMapping("/{id}")
    public MonthlyReportResponse get(@PathVariable UUID id) {
        return monthlyReportService.get(currentUserId(), id);
    }

    private UUID currentUserId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
