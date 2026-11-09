package com.examprep.web;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Display helpers used from Thymeleaf so that templates stay free of formatting logic. */
@Component("fmt")
public class Formatting {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    public String date(Instant instant) {
        return instant == null ? "\u2014" : DATE.format(instant);
    }

    public String dateTime(Instant instant) {
        return instant == null ? "\u2014" : DATE_TIME.format(instant);
    }

    public String duration(int seconds) {
        if (seconds <= 0) {
            return "0 min";
        }
        int minutes = seconds / 60;
        int remaining = seconds % 60;
        if (minutes == 0) {
            return remaining + " sec";
        }
        return remaining == 0 ? minutes + " min" : minutes + " min " + remaining + " sec";
    }

    public String fileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kilobytes = bytes / 1024.0;
        if (kilobytes < 1024) {
            return String.format(Locale.ENGLISH, "%.0f KB", kilobytes);
        }
        return String.format(Locale.ENGLISH, "%.1f MB", kilobytes / 1024.0);
    }

    public String label(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        String lower = status.toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public String initials(String name) {
        if (name == null || name.isBlank()) {
            return "EP";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String second = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ENGLISH);
    }

    public String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, Math.max(0, max - 1)) + "\u2026";
    }
}
