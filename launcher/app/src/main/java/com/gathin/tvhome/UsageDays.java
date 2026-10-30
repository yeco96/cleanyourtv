package com.gathin.tvhome;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a span at midnight (or at another hour of the day) into the days it covers, so a film that starts at 23:00 and
 * ends at 01:00 counts one hour on each day. No Android types; tested on the JVM.
 */
final class UsageDays {
    private UsageDays() { }

    /** Seconds of one span that fall on one day. {@code day} is an ISO date, such as 2026-10-30. */
    static final class Part {
        final String day;
        final long seconds;

        Part(String day, long seconds) {
            this.day = day;
            this.seconds = seconds;
        }
    }

    /**
     * The days a span covers. {@code shiftHours} moves the day's edge: 0 is the calendar day, 5 makes a day run from
     * 05:00 to 05:00, which is how the daughter's limit counts (a film that ends at 02:00 is still "yesterday's").
     */
    static List<Part> split(long startMs, long endMs, ZoneId zone, int shiftHours) {
        List<Part> parts = new ArrayList<>();
        long cursor = startMs;
        while (cursor < endMs) {
            LocalDate day = Instant.ofEpochMilli(cursor).atZone(zone).minusHours(shiftHours).toLocalDate();
            long boundary = day.plusDays(1).atStartOfDay(zone).plusHours(shiftHours).toInstant().toEpochMilli();
            long segmentEnd = Math.min(endMs, boundary > cursor ? boundary : endMs);
            long seconds = (segmentEnd - cursor) / 1000;
            if (seconds > 0) parts.add(new Part(day.toString(), seconds));
            cursor = segmentEnd;
        }
        return parts;
    }

    /** The day a moment belongs to, with the same shift. */
    static String dayOf(long timeMs, ZoneId zone, int shiftHours) {
        return Instant.ofEpochMilli(timeMs).atZone(zone).minusHours(shiftHours).toLocalDate().toString();
    }
}
