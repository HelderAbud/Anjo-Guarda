package com.anjoguarda.imports;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ImportLines {

    private static final Pattern OBSERVATION = Pattern.compile(
            "^OBSERVACAO (\\d{4}-\\d{2}-\\d{2}) (ROTINA|ESCOLA|ATIVIDADE|SAUDE|VIAGEM|TROCA_DE_HORARIO|OUTRO) (.+)$");
    private static final Pattern CONFIRMATION = Pattern.compile(
            "^CONFIRMACAO (\\d{4}-\\d{2}-\\d{2}) (REALIZADO|NAO_REALIZADO)$");
    private static final Pattern CHANGED = Pattern.compile(
            "^CONFIRMACAO (\\d{4}-\\d{2}-\\d{2}) ALTERADO "
                    + "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
                    + "(?: (\\d{2}:\\d{2}) (\\d{2}:\\d{2}))?$");
    private static final Pattern EXCEPTION = Pattern.compile(
            "^EXCECAO (\\d{4}-\\d{2}-\\d{2}) "
                    + "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}) (.+)$");

    private ImportLines() {}

    static List<Parsed> read(String text) {
        List<Parsed> parsed = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return parsed;
        }
        for (String raw : text.split("\\R")) {
            Parsed line = parse(raw.trim());
            if (line != null) {
                parsed.add(line);
            }
        }
        return parsed;
    }

    private static Parsed parse(String line) {
        if (line.isEmpty()) {
            return null;
        }
        Matcher observation = OBSERVATION.matcher(line);
        if (observation.matches() && date(observation.group(1)) != null) {
            return new Parsed("OBSERVACAO", map(
                    "date", observation.group(1),
                    "category", observation.group(2),
                    "text", observation.group(3).trim()));
        }
        Matcher confirmation = CONFIRMATION.matcher(line);
        if (confirmation.matches() && date(confirmation.group(1)) != null) {
            return new Parsed("CONFIRMACAO", map("date", confirmation.group(1), "status", confirmation.group(2)));
        }
        Matcher changed = CHANGED.matcher(line);
        if (changed.matches() && date(changed.group(1)) != null && uuid(changed.group(2)) != null) {
            Map<String, Object> data = map(
                    "date", changed.group(1),
                    "status", "ALTERADO",
                    "realizedGuardianId", changed.group(2));
            if (changed.group(3) != null) {
                data.put("realizedStartTime", changed.group(3));
                data.put("realizedEndTime", changed.group(4));
            }
            return new Parsed("CONFIRMACAO", data);
        }
        Matcher exception = EXCEPTION.matcher(line);
        if (exception.matches() && date(exception.group(1)) != null && uuid(exception.group(2)) != null) {
            return new Parsed("EXCECAO", map(
                    "date", exception.group(1),
                    "newGuardianId", exception.group(2),
                    "reason", exception.group(3).trim()));
        }
        return null;
    }

    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static Map<String, Object> map(String... pairs) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            data.put(pairs[index], pairs[index + 1]);
        }
        return data;
    }

    record Parsed(String type, Map<String, Object> data) {}
}
