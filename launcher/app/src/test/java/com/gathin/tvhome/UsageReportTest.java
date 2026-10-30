package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class UsageReportTest {
    private static final List<UsageReport.Row> ROWS = Arrays.asList(
            new UsageReport.Row("2026-10-28", "netflix", UsageReport.ADULT, 3600),
            new UsageReport.Row("2026-10-28", "youtube", UsageReport.CHILD, 1800),
            new UsageReport.Row("2026-10-29", "youtube", UsageReport.CHILD, 900),
            new UsageReport.Row("2026-10-29", "youtube", UsageReport.ADULT, 600),
            new UsageReport.Row("2026-10-30", "netflix", UsageReport.ADULT, 7200));

    @Test
    public void totalsCanBeOfOneProfileOrOfAll() {
        assertEquals(14100, UsageReport.total(ROWS, null));
        assertEquals(2700, UsageReport.total(ROWS, UsageReport.CHILD));
        assertEquals(11400, UsageReport.total(ROWS, UsageReport.ADULT));
    }

    @Test
    public void appsComeMostUsedFirstAndAreSummedAcrossDays() {
        List<UsageReport.AppTotal> apps = UsageReport.byApp(ROWS, null);
        assertEquals("netflix", apps.get(0).pkg);
        assertEquals(10800, apps.get(0).seconds);
        assertEquals("youtube", apps.get(1).pkg);
        assertEquals(3300, apps.get(1).seconds);
    }

    @Test
    public void aDayWithNoRowsIsZero() {
        long[] days = UsageReport.perDay(ROWS, LocalDate.of(2026, 10, 27), 5, null);
        assertArrayEquals(new long[]{0, 5400, 1500, 7200, 0}, days);
    }

    @Test
    public void rowsOutsideTheRangeAreLeftOut() {
        long[] days = UsageReport.perDay(ROWS, LocalDate.of(2026, 10, 29), 1, null);
        assertArrayEquals(new long[]{1500}, days);
    }

    @Test
    public void theAverageIgnoresDaysWithNoUse() {
        assertEquals(1500, UsageReport.averageOfUsedDays(new long[]{0, 1000, 0, 2000}));
        assertEquals(0, UsageReport.averageOfUsedDays(new long[]{0, 0}));
        assertEquals(2000, UsageReport.max(new long[]{0, 1000, 2000}));
    }

    @Test
    public void thePercentChangeIsAgainstTheBefore() {
        assertEquals(50, UsageReport.changePercent(150, 100));
        assertEquals(-25, UsageReport.changePercent(75, 100));
        assertEquals(0, UsageReport.changePercent(100, 0));
    }
}
