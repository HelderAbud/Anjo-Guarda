package com.anjoguarda.calendar;

import com.anjoguarda.schedule.RuleType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;

public final class CustodyDayCalculator {

    public UUID guardianOn(
            LocalDate date, LocalDate start, LocalDate end, RuleType ruleType, Map<String, UUID> configuration) {
        if (date.isBefore(start) || (end != null && date.isAfter(end))) {
            return null;
        }
        return switch (ruleType) {
            case ALTERNATE_DAYS -> ChronoUnit.DAYS.between(start, date) % 2 == 0
                    ? configuration.get("start")
                    : configuration.get("alternate");
            case WEEKENDS -> weekend(date) ? configuration.get("weekend") : configuration.get("weekday");
            case ALTERNATE_WEEKS -> ChronoUnit.WEEKS.between(mondayOf(start), mondayOf(date)) % 2 == 0
                    ? configuration.get("startWeek")
                    : configuration.get("alternateWeek");
            case CUSTOM -> configuration.get(date.getDayOfWeek().name());
        };
    }

    private boolean weekend(LocalDate date) {
        DayOfWeek weekday = date.getDayOfWeek();
        return weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY;
    }

    private LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
