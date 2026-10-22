package com.anjoguarda.storage;

import com.anjoguarda.calendar.BrazilTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class StorageKeys {

    private StorageKeys() {}

    public static String key(Instant instant, UUID id, String extension) {
        LocalDate date = instant.atZone(BrazilTime.ZONE).toLocalDate();
        return date.getYear() + "/" + twoDigits(date.getMonthValue()) + "/" + id + extension;
    }

    private static String twoDigits(int month) {
        return month < 10 ? "0" + month : Integer.toString(month);
    }
}
