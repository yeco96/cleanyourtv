package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class KidsPolicyTest {
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList("kids.app"));
    private static final Set<String> KEYBOARDS = new HashSet<>(Arrays.asList("the.keyboard"));

    @Test
    public void fiveWrongPinsAreFreeThenTheWaitDoublesUpToFiveMinutes() {
        assertEquals(0, KidsPolicy.waitAfter(4));
        assertEquals(30_000, KidsPolicy.waitAfter(5));
        assertEquals(60_000, KidsPolicy.waitAfter(6));
        assertEquals(120_000, KidsPolicy.waitAfter(7));
        assertEquals(300_000, KidsPolicy.waitAfter(20));
    }

    @Test
    public void childrensAppsStartTickedButNotTheFullYouTube() {
        assertTrue(KidsPolicy.kidsApp("com.google.android.youtube.tvkids", "YouTube Kids"));
        assertTrue(KidsPolicy.kidsApp("some.app", "Disney Junior"));
        assertFalse(KidsPolicy.kidsApp("com.google.android.youtube.tv", "YouTube"));
        assertFalse(KidsPolicy.kidsApp("com.netflix.ninja", "Netflix"));
        assertFalse(KidsPolicy.kidsApp(null, null));
    }

    @Test
    public void theGuardOnlyBouncesAppsThatAreNotAllowed() {
        assertFalse(KidsPolicy.bounce(false, "netflix", "me", ALLOWED, KEYBOARDS));
        assertTrue(KidsPolicy.bounce(true, "netflix", "me", ALLOWED, KEYBOARDS));
        assertFalse(KidsPolicy.bounce(true, "kids.app", "me", ALLOWED, KEYBOARDS));
        assertFalse(KidsPolicy.bounce(true, "me", "me", ALLOWED, KEYBOARDS));
        assertFalse(KidsPolicy.bounce(true, "the.keyboard", "me", ALLOWED, KEYBOARDS));
        assertFalse(KidsPolicy.bounce(true, "com.android.systemui", "me", ALLOWED, KEYBOARDS));
        assertFalse(KidsPolicy.bounce(true, "android", "me", ALLOWED, Collections.emptySet()));
        assertFalse(KidsPolicy.bounce(true, null, "me", ALLOWED, KEYBOARDS));
    }
}
