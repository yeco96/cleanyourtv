package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

public class WallpaperShuffleTest {

    private static List<File> pictures(int count) {
        List<File> files = new ArrayList<>();
        for (int i = 1; i <= count; i++) files.add(new File(String.format("%02d.jpg", i)));
        return files;
    }

    @Test
    public void everyPictureComesOnceBeforeAnyComesBack() {
        List<File> files = pictures(20);
        Set<String> shown = new HashSet<>();
        Random random = new Random(7);
        Set<String> round = new HashSet<>();
        String previous = "";
        for (int i = 0; i < 20; i++) {
            File next = WallpaperRotation.pickFresh(files, shown, previous, random);
            assertTrue("repeated inside a round: " + next, round.add(next.getName()));
            previous = next.getName();
        }
        assertEquals(20, round.size());
    }

    @Test
    public void theNextRoundDoesNotOpenWithTheLastPicture() {
        List<File> files = pictures(5);
        for (long seed = 0; seed < 50; seed++) {
            Set<String> shown = new HashSet<>();
            Random random = new Random(seed);
            String previous = "";
            for (int i = 0; i < 5; i++) previous = WallpaperRotation.pickFresh(files, shown, previous, random).getName();
            File opener = WallpaperRotation.pickFresh(files, shown, previous, random);
            assertNotEquals(previous, opener.getName());
        }
    }

    @Test
    public void aSinglePictureRepeatsAndNothingHasNoPicture() {
        Set<String> shown = new HashSet<>();
        List<File> one = pictures(1);
        assertEquals("01.jpg", WallpaperRotation.pickFresh(one, shown, "", new Random(1)).getName());
        assertEquals("01.jpg", WallpaperRotation.pickFresh(one, shown, "01.jpg", new Random(1)).getName());
        assertNull(WallpaperRotation.pickFresh(new ArrayList<>(), shown, "", new Random(1)));
    }

    @Test
    public void aPictureThatLeftTheFolderDoesNotStallTheRound() {
        Set<String> shown = new HashSet<>();
        shown.add("gone.jpg");
        List<File> files = pictures(3);
        File next = WallpaperRotation.pickFresh(files, shown, "", new Random(3));
        assertTrue(next.getName().endsWith(".jpg"));
    }
}
