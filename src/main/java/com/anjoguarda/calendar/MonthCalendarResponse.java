package com.anjoguarda.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MonthCalendarResponse(UUID childId, int year, int month, String timeZone, List<DayResponse> days) {

    public record DayResponse(LocalDate date, String weekday, UUID guardianId, boolean exception) {}
}

