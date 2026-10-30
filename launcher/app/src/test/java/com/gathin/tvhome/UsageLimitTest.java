package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UsageLimitTest {
    @Test
    public void theHourNoticesComeAtOneAndTwoHours() {
        assertEquals(0, UsageLimit.hoursReached(59 * 60));
        assertEquals(1, UsageLimit.hoursReached(60 * 60));
        assertEquals(1, UsageLimit.hoursReached(119 * 60));
        assertEquals(2, UsageLimit.hoursReached(120 * 60));
        assertEquals(2, UsageLimit.hoursReached(500 * 60));
        assertEquals(0, UsageLimit.hoursReached(-5));
    }

    @Test
    public void aPinIsFourDigitsAndIsOnlyKeptHashed() {
        assertTrue(UsageLimit.validPin("0420"));
        assertFalse(UsageLimit.validPin("42"));
        assertFalse(UsageLimit.validPin("04a0"));
        assertFalse(UsageLimit.validPin(null));
        String stored = UsageLimit.hash("0420", "salt1");
        assertFalse(stored.contains("0420"));
        assertTrue(UsageLimit.matches("0420", "salt1", stored));
        assertFalse(UsageLimit.matches("0421", "salt1", stored));
        assertNotEquals(stored, UsageLimit.hash("0420", "salt2"));
        assertFalse(UsageLimit.matches("0420", "salt1", null));
    }
}
