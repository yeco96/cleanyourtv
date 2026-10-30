package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

public class WallpaperCaptionsTest {

    @Test
    public void readsFilenameTabName() {
        Map<String, String> names = WallpaperCaptions.parse(Arrays.asList(
                "001.jpg\tRed Spider Nebula · Hubble",
                "002.jpg\t  Falcon 9 at sunset  "));
        assertEquals("Red Spider Nebula · Hubble", names.get("001.jpg"));
        assertEquals("Falcon 9 at sunset", names.get("002.jpg"));
    }

    @Test
    public void skipsCommentsBlankAndMalformedLines() {
        Map<String, String> names = WallpaperCaptions.parse(Arrays.asList(
                "# captions for the collection", "", "   ", "no-tab-here.jpg Dusk", "\tno file", "003.jpg\t", null));
        assertTrue(names.isEmpty());
    }

    @Test
    public void cutsVeryLongNames() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 30; i++) longName.append("word ");
        Map<String, String> names = WallpaperCaptions.parse(Arrays.asList("004.jpg\t" + longName));
        assertTrue(names.get("004.jpg").length() <= WallpaperCaptions.MAX_LENGTH);
    }

    @Test
    public void aNameStopsAtTheNextTab() {
        Map<String, String> names = WallpaperCaptions.parse(Arrays.asList("007.jpg\tDune\t★★★★ 8.1 • 2021\tA duke's son."));
        assertEquals("Dune", names.get("007.jpg"));
    }

    @Test
    public void readsTitleFactsAndOverview() {
        Map<String, WallpaperCaptions.Movie> movies = WallpaperCaptions.parseMovies(Arrays.asList(
                "01_1.jpg\tDune\t★★★★ 8.1 • 2021 • 2h 35m\tA duke's son."));
        WallpaperCaptions.Movie dune = movies.get("01_1.jpg");
        assertEquals("Dune", dune.title);
        assertEquals("★★★★ 8.1 • 2021 • 2h 35m", dune.facts);
        assertEquals("A duke's son.", dune.overview);
        assertTrue(dune.hasDetails());
    }

    @Test
    public void aMovieWithoutFactsOrOverviewHasNoDetails() {
        Map<String, WallpaperCaptions.Movie> movies = WallpaperCaptions.parseMovies(Arrays.asList("02.jpg\tDusk"));
        assertEquals("", movies.get("02.jpg").facts);
        assertEquals("", movies.get("02.jpg").overview);
        assertFalse(movies.get("02.jpg").hasDetails());
    }

    @Test
    public void shortensAtTheEndOfASentence() {
        String text = "Riley es una chica que disfruta. Aunque su vida ha estado marcada por la Alegria, tambien se ve afectada.";
        assertEquals("Riley es una chica que disfruta.", WallpaperCaptions.shorten(text, 60));
    }

    @Test
    public void shortensAtAWordWhenNoSentenceEndsInTheSecondHalf() {
        String text = "Una joven mecanica de autos de carreras finge estar comprometida con la estrella mas polemica";
        String cut = WallpaperCaptions.shorten(text, 50);
        assertTrue(cut.endsWith("..."));
        assertTrue(cut.length() <= 50);
        assertFalse(cut.contains("compro"));
    }

    @Test
    public void aShortTextIsLeftAlone() {
        assertEquals("Dune.", WallpaperCaptions.shorten("  Dune.  ", 140));
        assertEquals("", WallpaperCaptions.shorten(null, 140));
    }

    @Test
    public void laterLineWinsForTheSameFile() {
        Map<String, String> names = WallpaperCaptions.parse(Arrays.asList("005.jpg\tOld", "005.jpg\tNew"));
        assertEquals("New", names.get("005.jpg"));
        assertFalse(names.containsKey("006.jpg"));
    }

    @Test
    public void missingFolderHasNoNames() {
        assertTrue(WallpaperCaptions.read(null).isEmpty());
    }
}
