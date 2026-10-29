package com.ultimatelogs.utils;

import com.ultimatelogs.config.LogsConfig;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Utility for formatting real-world date, time, and precise duration intervals.
 * Handles display like: "Date: 22/10/2026, Interval=12:01:02-12:01:05 (3s)"
 */
public class TimeIntervalFormatter {

    private final LogsConfig config;
    private final DateTimeFormatter dateFormatter;
    private final DateTimeFormatter timeFormatter;

    public TimeIntervalFormatter(LogsConfig config) {
        this.config = config;
        this.dateFormatter = DateTimeFormatter.ofPattern(config.getDateFormat()).withZone(config.getZoneId());
        this.timeFormatter = DateTimeFormatter.ofPattern(config.getTimeFormat()).withZone(config.getZoneId());
    }

    public ZoneId getZoneId() {
        return config.getZoneId();
    }

    public String formatDate(Instant instant) {
        if (instant == null) instant = Instant.now();
        return dateFormatter.format(instant);
    }

    public String formatTime(Instant instant) {
        if (instant == null) instant = Instant.now();
        return timeFormatter.format(instant);
    }

    /**
     * Formats an interval string: e.g. "Interval=12:01:02-12:01:05"
     */
    public String formatInterval(Instant start, Instant end) {
        if (start == null) start = end != null ? end : Instant.now();
        if (end == null) end = start;
        return "Interval=" + timeFormatter.format(start) + "-" + timeFormatter.format(end);
    }

    /**
     * Formats a complete date and interval string:
     * e.g. "Date: 22/10/2026, Interval=12:01:02-12:01:05"
     */
    public String formatFullIntervalString(Instant start, Instant end) {
        if (start == null) start = end != null ? end : Instant.now();
        if (end == null) end = start;
        return "Date: " + formatDate(start) + ", " + formatInterval(start, end);
    }

    /**
     * Calculates duration and formats:
     * e.g. "Date: 22/10/2026, Interval=12:01:02-12:01:05 (Duration: 3s)"
     */
    public String formatIntervalWithDuration(Instant start, Instant end, int durationSeconds) {
        if (end == null) end = Instant.now();
        if (start == null) {
            start = end.minusSeconds(Math.max(1, durationSeconds));
        }
        long secs = durationSeconds > 0 ? durationSeconds : Math.max(1, Duration.between(start, end).getSeconds());
        return "Date: " + formatDate(start) + ", " + formatInterval(start, end) + " (" + formatDuration(secs) + ")";
    }

    /**
     * Human-readable duration format (e.g. 5s, 2m 14s, 1h 30m)
     */
    public String formatDuration(long totalSeconds) {
        if (totalSeconds <= 1) return "1s (Instant)";
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    /**
     * Builds standardized GUI lore lines for a recorded snapshot or log item.
     */
    public List<String> buildIntervalLore(Instant start, Instant end, int durationSeconds) {
        if (end == null) end = Instant.now();
        if (start == null) {
            start = end.minusSeconds(Math.max(1, durationSeconds));
        }
        long secs = durationSeconds > 0 ? durationSeconds : Math.max(1, Duration.between(start, end).getSeconds());
        return List.of(
                "<gray>Date:</gray> <white>" + formatDate(start) + "</white>",
                "<gray>Interval:</gray> <yellow>" + timeFormatter.format(start) + " - " + timeFormatter.format(end) + "</yellow> <dark_gray>(" + formatDuration(secs) + ")</dark_gray>",
                "<gray>Real Time:</gray> <gold>" + timeFormatter.format(end) + "</gold>"
        );
    }
}
