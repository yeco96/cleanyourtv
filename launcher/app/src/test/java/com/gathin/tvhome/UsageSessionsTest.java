package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class UsageSessionsTest {
    private static final long MIN = 60_000L;
    private static final long MAX = 6 * 60 * MIN;
    private static final Set<String> IGNORED = new HashSet<>(Arrays.asList("home", "dream"));

    private static UsageSessions.Event resumed(long t, String pkg) {
        return new UsageSessions.Event(t, UsageSessions.RESUMED, pkg, pkg + ".Main");
    }

    private static UsageSessions.Event paused(long t, String pkg) {
        return new UsageSessions.Event(t, UsageSessions.PAUSED, pkg, pkg + ".Main");
    }

    private static List<UsageSessions.Span> fold(UsageSessions.State state, long now, UsageSessions.Event... events) {
        return UsageSessions.fold(state, new ArrayList<>(Arrays.asList(events)), now, IGNORED, MAX);
    }

    @Test
    public void anAppInFrontIsOneSpanFromResumeToPause() {
        List<UsageSessions.Span> spans = fold(new UsageSessions.State(), 100 * MIN, resumed(10 * MIN, "netflix"), paused(40 * MIN, "netflix"));
        assertEquals(1, spans.size());
        assertEquals("netflix", spans.get(0).pkg);
        assertEquals(30 * 60, spans.get(0).seconds());
    }

    @Test
    public void anotherAppTakingTheScreenEndsTheFirst() {
        List<UsageSessions.Span> spans = fold(new UsageSessions.State(), 100 * MIN,
                resumed(0, "netflix"), resumed(20 * MIN, "youtube"), paused(25 * MIN, "youtube"));
        assertEquals(2, spans.size());
        assertEquals(20 * 60, spans.get(0).seconds());
        assertEquals(5 * 60, spans.get(1).seconds());
    }

    @Test
    public void theLauncherAndTheScreensaverNeverCountButStillEndTheApp() {
        List<UsageSessions.Span> spans = fold(new UsageSessions.State(), 100 * MIN,
                resumed(0, "netflix"), resumed(10 * MIN, "home"), resumed(50 * MIN, "dream"), paused(60 * MIN, "dream"));
        assertEquals(1, spans.size());
        assertEquals("netflix", spans.get(0).pkg);
        assertEquals(10 * 60, spans.get(0).seconds());
    }

    @Test
    public void theScreenGoingOffEndsTheSpan() {
        List<UsageSessions.Span> spans = fold(new UsageSessions.State(), 100 * MIN,
                resumed(0, "netflix"), new UsageSessions.Event(30 * MIN, UsageSessions.SCREEN_OFF, null, null));
        assertEquals(30 * 60, spans.get(0).seconds());
    }

    @Test
    public void anotherScreenOfTheSameAppDoesNotSplitTheSpan() {
        UsageSessions.State state = new UsageSessions.State();
        List<UsageSessions.Span> spans = fold(state, 100 * MIN,
                resumed(0, "netflix"),
                new UsageSessions.Event(10 * MIN, UsageSessions.RESUMED, "netflix", "netflix.Player"),
                new UsageSessions.Event(40 * MIN, UsageSessions.PAUSED, "netflix", "netflix.Player"));
        assertEquals(1, spans.size());
        assertEquals(40 * 60, spans.get(0).seconds());
    }

    @Test
    public void aPauseOfAScreenAlreadyReplacedDoesNotEndTheApp() {
        List<UsageSessions.Span> spans = fold(new UsageSessions.State(), 100 * MIN,
                resumed(0, "netflix"),
                new UsageSessions.Event(5 * MIN, UsageSessions.RESUMED, "netflix", "netflix.Player"),
                new UsageSessions.Event(5 * MIN + 1, UsageSessions.PAUSED, "netflix", "netflix.Main"),
                new UsageSessions.Event(30 * MIN, UsageSessions.PAUSED, "netflix", "netflix.Player"));
        assertEquals(1, spans.size());
        assertEquals(30 * 60, spans.get(0).seconds());
    }

    @Test
    public void anAppStillOpenIsCountedUpToNowAndOnlyOnce() {
        UsageSessions.State state = new UsageSessions.State();
        List<UsageSessions.Span> first = fold(state, 30 * MIN, resumed(0, "netflix"));
        assertEquals(30 * 60, first.get(0).seconds());
        assertTrue(state.isOpen());
        List<UsageSessions.Span> second = fold(state, 50 * MIN);
        assertEquals(20 * 60, second.get(0).seconds());
        List<UsageSessions.Span> third = fold(state, 60 * MIN, paused(55 * MIN, "netflix"));
        assertEquals(5 * 60, third.get(0).seconds());
        assertFalse(state.isOpen());
    }

    @Test
    public void anAppLeftOnIsNotCountedPastTheCap() {
        UsageSessions.State state = new UsageSessions.State();
        List<UsageSessions.Span> spans = fold(state, 20 * 60 * MIN, resumed(0, "netflix"));
        assertEquals(MAX / 1000, spans.get(0).seconds());
        assertFalse(state.isOpen());
    }

    @Test
    public void nothingHappenedMeansNothingCounted() {
        assertTrue(fold(new UsageSessions.State(), 10 * MIN).isEmpty());
        assertTrue(UsageSessions.fold(new UsageSessions.State(), Collections.emptyList(), 0, IGNORED, MAX).isEmpty());
    }

    @Test
    public void aSpanAcrossMidnightIsSharedBetweenTheTwoDays() {
        ZoneId zone = ZoneId.of("UTC");
        long start = java.time.LocalDateTime.of(2026, 10, 29, 23, 0).atZone(zone).toInstant().toEpochMilli();
        List<UsageDays.Part> parts = UsageDays.split(start, start + 120 * MIN, zone, 0);
        assertEquals(2, parts.size());
        assertEquals("2026-10-29", parts.get(0).day);
        assertEquals(3600, parts.get(0).seconds);
        assertEquals("2026-10-30", parts.get(1).day);
        assertEquals(3600, parts.get(1).seconds);
    }

    @Test
    public void aShiftedDayEndsAtTheShiftHour() {
        ZoneId zone = ZoneId.of("UTC");
        long start = java.time.LocalDateTime.of(2026, 10, 30, 4, 0).atZone(zone).toInstant().toEpochMilli();
        List<UsageDays.Part> parts = UsageDays.split(start, start + 120 * MIN, zone, 5);
        assertEquals("2026-10-29", parts.get(0).day);   // 04:00 still belongs to the day that began at 05:00 yesterday
        assertEquals(3600, parts.get(0).seconds);
        assertEquals("2026-10-30", parts.get(1).day);
        assertEquals(3600, parts.get(1).seconds);
        assertEquals("2026-10-29", UsageDays.dayOf(start, zone, 5));
    }
}
