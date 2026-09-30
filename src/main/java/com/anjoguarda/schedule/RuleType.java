package com.anjoguarda.schedule;

import java.util.Set;

public enum RuleType {
    ALTERNATE_DAYS(Set.of("start", "alternate")),
    WEEKENDS(Set.of("weekday", "weekend")),
    ALTERNATE_WEEKS(Set.of("startWeek", "alternateWeek")),
    CUSTOM(Set.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"));

    private final Set<String> keys;

    RuleType(Set<String> keys) {
        this.keys = keys;
    }

    public Set<String> keys() {
        return keys;
    }
}
