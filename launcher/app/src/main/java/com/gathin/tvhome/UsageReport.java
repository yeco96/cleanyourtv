package com.gathin.tvhome;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The totals the stats screen shows, from the rows UsageStore keeps (day, app, profile, seconds). No Android types;
 * tested on the JVM.
 */
final class UsageReport {
    static final String ADULT = "adulto";
    static final String CHILD = "limitado";

    private UsageReport() { }

    static final class Row {
        final String day;
        final String pkg;
        final String profile;
        final long seconds;

        Row(String day, String pkg, String profile, long seconds) {
            this.day = day;
            this.pkg = pkg;
            this.profile = profile;
            this.seconds = seconds;
        }
    }

    static final class AppTotal {
        final String pkg;
        final long seconds;

        AppTotal(String pkg, long seconds) {
            this.pkg = pkg;
            this.seconds = seconds;
        }
    }

    /** True when {@code filter} is null (every profile) or is the row's profile. */
    private static boolean matches(Row row, String filter) {
        return filter == null || filter.equals(row.profile);
    }

    /** Seconds in all the rows, of one profile or (with null) of every profile. */
    static long total(List<Row> rows, String profile) {
        long sum = 0;
        for (Row row : rows) if (matches(row, profile)) sum += row.seconds;
        return sum;
    }

    /** Seconds per app, the most used first. */
    static List<AppTotal> byApp(List<Row> rows, String profile) {
        Map<String, Long> sums = new HashMap<>();
        for (Row row : rows) {
            if (matches(row, profile)) sums.merge(row.pkg, row.seconds, Long::sum);
        }
        List<AppTotal> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : sums.entrySet()) out.add(new AppTotal(e.getKey(), e.getValue()));
        Collections.sort(out, (a, b) -> a.seconds != b.seconds ? Long.compare(b.seconds, a.seconds) : a.pkg.compareTo(b.pkg));
        return out;
    }

    /** Seconds on each of {@code days} days starting at {@code from}, zero for a day with no rows. */
    static long[] perDay(List<Row> rows, LocalDate from, int days, String profile) {
        long[] out = new long[days];
        for (Row row : rows) {
            if (!matches(row, profile)) continue;
            long offset = LocalDate.parse(row.day).toEpochDay() - from.toEpochDay();
            if (offset >= 0 && offset < days) out[(int) offset] += row.seconds;
        }
        return out;
    }

    /** The average of the days that had any use; 0 when none did. */
    static long averageOfUsedDays(long[] perDay) {
        long sum = 0;
        int used = 0;
        for (long seconds : perDay) {
            if (seconds > 0) {
                sum += seconds;
                used++;
            }
        }
        return used == 0 ? 0 : sum / used;
    }

    static long max(long[] values) {
        long max = 0;
        for (long v : values) max = Math.max(max, v);
        return max;
    }

    /** How much more (positive) or less (negative) this period is than the one before, in percent; 0 with no base. */
    static int changePercent(long now, long before) {
        if (before <= 0) return 0;
        return (int) Math.round((now - before) * 100.0 / before);
    }
}
