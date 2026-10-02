package com.anjoguarda.calendar;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calendar/exceptions")
public class CalendarExceptionController {

    private final CalendarExceptionService calendarExceptionService;

    public CalendarExceptionController(CalendarExceptionService calendarExceptionService) {
        this.calendarExceptionService = calendarExceptionService;
    }

    @PostMapping
    public ResponseEntity<CalendarExceptionResponse> upsert(@Valid @RequestBody CreateCalendarExceptionRequest request) {
        CalendarExceptionService.UpsertResult result = calendarExceptionService.upsert(currentUserId(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.body());
    }

    private UUID currentUserId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
