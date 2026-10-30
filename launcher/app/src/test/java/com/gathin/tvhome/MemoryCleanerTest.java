package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MemoryCleanerTest {

    @Test
    public void anAppTheOwnerInstalledMayBeClosed() {
        assertTrue(MemoryCleaner.killable(true, false, false, false, false));
    }

    @Test
    public void aSystemAppUpdatedFromTheStoreMayBeClosed() {
        assertTrue(MemoryCleaner.killable(true, true, true, false, false));
    }

    @Test
    public void aPlainSystemAppIsLeftAlone() {
        assertFalse(MemoryCleaner.killable(true, true, false, false, false));
    }

    @Test
    public void theLauncherNeverClosesItself() {
        assertFalse(MemoryCleaner.killable(true, false, false, true, false));
    }

    @Test
    public void anAppWithAnAccessibilityServiceIsLeftAlone() {
        assertFalse(MemoryCleaner.killable(true, false, false, false, true));
    }

    @Test
    public void somethingTheOwnerCannotOpenIsLeftAlone() {
        assertFalse(MemoryCleaner.killable(false, false, false, false, false));
    }
}
