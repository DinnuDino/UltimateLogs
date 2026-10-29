package com.ultimatelogs.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeParser {

    private static final Pattern RELATIVE_PATTERN = Pattern.compile("(\\d+)\\s*([smhdw]|sec|min|hr|hour|day|week)s?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_OF_DAY_PATTERN = Pattern.compile("^([01]?\\d|2[0-3]):([0-5]\\d)(?::([0-5]\\d))?$");

    private final ZoneId zoneId;
    private final DateTimeFormatter[] datePatterns;

    public TimeParser(ZoneId zoneId) {
        this.zoneId = zoneId != null ? zoneId : ZoneId.systemDefault();
        this.datePatterns = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(this.zoneId),
                DateTimeFormatter.ofPattern("dd/MM/yyyy_HH:mm:ss").withZone(this.zoneId),
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").withZone(this.zoneId),
                DateTimeFormatter.ofPattern("dd-MM-yyyy_HH:mm:ss").withZone(this.zoneId),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(this.zoneId),
                DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss").withZone(this.zoneId),
                DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(this.zoneId),
                DateTimeFormatter.ofPattern("dd-MM-yyyy").withZone(this.zoneId),
                DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(this.zoneId),
                DateTimeFormatter.ISO_INSTANT
        };
    }

    public Instant parse(String input) {
        if (input == null || input.isBlank()) return null;
        String raw = input.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        if (lower.equals("now") || lower.equals("latest") || lower.equals("live") || lower.equals("current")) {
            return Instant.now();
        }
        if (lower.equals("yesterday")) {
            return Instant.now().minus(1, ChronoUnit.DAYS);
        }

        // Relative pattern: e.g. "10m", "1h", "2d", "30s"
        Matcher relMatcher = RELATIVE_PATTERN.matcher(raw);
        if (relMatcher.find()) {
            long totalSeconds = 0;
            do {
                long val = Long.parseLong(relMatcher.group(1));
                String unit = relMatcher.group(2).toLowerCase(Locale.ROOT);
                if (unit.startsWith("s")) totalSeconds += val;
                else if (unit.startsWith("m")) totalSeconds += val * 60;
                else if (unit.startsWith("h")) totalSeconds += val * 3600;
                else if (unit.startsWith("d")) totalSeconds += val * 86400;
                else if (unit.startsWith("w")) totalSeconds += val * 604800;
            } while (relMatcher.find());
            return Instant.now().minusSeconds(totalSeconds);
        }

        // Time of day: e.g. "15:30:00" or "15:30"
        Matcher timeMatcher = TIME_OF_DAY_PATTERN.matcher(raw);
        if (timeMatcher.matches()) {
            int h = Integer.parseInt(timeMatcher.group(1));
            int m = Integer.parseInt(timeMatcher.group(2));
            int s = timeMatcher.group(3) != null ? Integer.parseInt(timeMatcher.group(3)) : 0;
            LocalDate today = LocalDate.now(zoneId);
            LocalTime targetTime = LocalTime.of(h, m, s);
            ZonedDateTime zdt = ZonedDateTime.of(today, targetTime, zoneId);
            if (zdt.toInstant().isAfter(Instant.now())) {
                zdt = zdt.minusDays(1);
            }
            return zdt.toInstant();
        }

        // Date & Date-Time formatters
        for (DateTimeFormatter fmt : datePatterns) {
            try {
                return Instant.from(fmt.parse(raw));
            } catch (Exception ignored) {}
            try {
                LocalDate date = LocalDate.parse(raw, fmt);
                return date.atStartOfDay(zoneId).toInstant();
            } catch (Exception ignored) {}
        }

        return null;
    }
}
