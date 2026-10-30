package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class TmdbBackdropsTest {

    @Test
    public void theMoviesAreRenewedWhenTheDayChangesOrTooFewAreLeft() {
        assertTrue(TmdbBackdrops.due("2026-10-29", "2026-10-30", 20));
        assertTrue(TmdbBackdrops.due("", "2026-10-30", 20));
        assertFalse(TmdbBackdrops.due("2026-10-30", "2026-10-30", 20));
        assertTrue(TmdbBackdrops.due("2026-10-30", "2026-10-30", 2));
    }

    @Test
    public void anAutomaticRetryWaitsFifteenMinutes() {
        assertFalse(TmdbBackdrops.retryAllowed(10 * 60_000L, 0 + 5 * 60_000L));
        assertTrue(TmdbBackdrops.retryAllowed(20 * 60_000L, 5 * 60_000L));
        assertTrue(TmdbBackdrops.retryAllowed(1_000L, 99_000_000L));   // the clock went back
    }

    @Test
    public void aGoodLanguageIsKeptAndABadOneFallsBackToTheTvs() {
        assertEquals("es-MX", TmdbBackdrops.language("es-MX"));
        assertEquals("es", TmdbBackdrops.language("es"));
        String fallback = TmdbBackdrops.language("es_MX; drop table");
        assertEquals(true, fallback.matches("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,4})?"));
    }

    @Test
    public void theCertificationIsOneOfTheKnownRatings() {
        assertEquals("PG", TmdbBackdrops.certification("pg"));
        assertEquals("R", TmdbBackdrops.certification(" R "));
        assertEquals("PG-13", TmdbBackdrops.certification("PG-13"));
    }

    @Test
    public void anUnknownOrMissingCertificationIsTheSafeDefault() {
        assertEquals("PG-13", TmdbBackdrops.certification(null));
        assertEquals("PG-13", TmdbBackdrops.certification(""));
        assertEquals("PG-13", TmdbBackdrops.certification("XXX&api_key=1"));
    }

    @Test
    public void anyTurnsTheFilterOff() {
        assertEquals("", TmdbBackdrops.certification("any"));
        assertEquals("", TmdbBackdrops.certification("ANY"));
    }

    @Test
    public void theTwoListsAreMixedOneAndOne() {
        List<String> mixed = TmdbBackdrops.interleave(Arrays.asList("p1", "p2", "p3"), Arrays.asList("f1", "f2"));
        assertEquals(Arrays.asList("p1", "f1", "p2", "f2", "p3"), mixed);
    }

    @Test
    public void aShortOrEmptyListLeavesTheOtherInOrder() {
        assertEquals(Arrays.asList("p1", "p2"), TmdbBackdrops.interleave(Arrays.asList("p1", "p2"), Collections.<String>emptyList()));
        assertEquals(Arrays.asList("f1"), TmdbBackdrops.interleave(Collections.<String>emptyList(), Arrays.asList("f1")));
        assertEquals(0, TmdbBackdrops.interleave(Collections.<String>emptyList(), Collections.<String>emptyList()).size());
    }
}
