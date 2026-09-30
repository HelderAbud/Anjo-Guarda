package com.anjoguarda.calendar;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/children/{childId}/calendar")
public class MonthCalendarController {

    private final MonthCalendarService monthCalendarService;

    public MonthCalendarController(MonthCalendarService monthCalendarService) {
        this.monthCalendarService = monthCalendarService;
    }

    @GetMapping
    public MonthCalendarResponse month(
            @PathVariable UUID childId, @RequestParam int year, @RequestParam int month) {
        return monthCalendarService.month(currentUserId(), childId, year, month);
    }

    private UUID currentUserId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
