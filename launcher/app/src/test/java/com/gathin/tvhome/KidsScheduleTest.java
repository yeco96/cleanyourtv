package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KidsScheduleTest {
    @Test
    public void offIsNeverInTheWindow() {
        assertFalse(KidsSchedule.inWindow(0, 16 * 60));
        assertFalse(KidsSchedule.inWindow(-1, 16 * 60));
        assertFalse(KidsSchedule.inWindow(99, 16 * 60));
    }

    @Test
    public void aWindowStartsAtItsFirstMinuteAndEndsBeforeItsLast() {
        assertFalse(KidsSchedule.inWindow(1, 14 * 60 + 59));   // 15:00-19:00
        assertTrue(KidsSchedule.inWindow(1, 15 * 60));
        assertTrue(KidsSchedule.inWindow(1, 18 * 60 + 59));
        assertFalse(KidsSchedule.inWindow(1, 19 * 60));
    }

    @Test
    public void choicesStepAndWrapBackToOff() {
        int choice = 0;
        for (int i = 0; i < KidsSchedule.count() - 1; i++) choice = KidsSchedule.next(choice);
        assertEquals(KidsSchedule.count() - 1, choice);
        assertEquals(0, KidsSchedule.next(choice));
        assertEquals(15, KidsSchedule.startHour(1));
        assertEquals(19, KidsSchedule.endHour(1));
    }
}
