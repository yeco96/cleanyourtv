package com.gathin.tvhome;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Names for a wallpaper collection (wallpaper_captions): {@code wallpapers/captions.txt}, one
 * "filename<TAB>name" per line. Blank lines, lines starting with # and lines without a tab are skipped; a name
 * is cut to {@link #MAX_LENGTH} characters. A line may carry two more tab-separated columns, a line of facts and an
 * overview (the TMDb panel, see {@link Movie}); the name is then the first column after the file. No Android types,
 * so it is tested on the JVM.
 */
final class WallpaperCaptions {
    static final String FILE_NAME = "captions.txt";
    static final int MAX_LENGTH = 80;

    /** A movie's title, its line of facts (stars, year, genres, runtime) and its overview, as the TMDb panel shows them. */
    static final class Movie {
        final String title;
        final String facts;
        final String overview;

        Movie(String title, String facts, String overview) {
            this.title = title;
            this.facts = facts;
            this.overview = overview;
        }

        /** The panel has something to say beyond the title. */
        boolean hasDetails() {
            return !facts.isEmpty() || !overview.isEmpty();
        }
    }

    private WallpaperCaptions() { }

    static Map<String, String> parse(List<String> lines) {
        Map<String, String> names = new HashMap<>();
        for (String line : lines) {
            String[] columns = columns(line);
            if (columns == null) continue;
            String name = columns[1];
            if (name.length() > MAX_LENGTH) name = name.substring(0, MAX_LENGTH).trim();
            names.put(columns[0], name);
        }
        return names;
    }

    /** The same lines read as movies: file, title, facts, overview; the last two may be missing. */
    static Map<String, Movie> parseMovies(List<String> lines) {
        Map<String, Movie> movies = new HashMap<>();
        for (String line : lines) {
            String[] columns = columns(line);
            if (columns == null) continue;
            movies.put(columns[0], new Movie(columns[1], columns[2], columns[3]));
        }
        return movies;
    }

    /**
     * The text cut to at most {@code max} characters at the end of a sentence when one falls in its second half,
     * else at a word with "...": a synopsis that stops mid-phrase reads as an error, one that ends on a full stop
     * does not.
     */
    static String shorten(String text, int max) {
        if (text == null) return "";
        String t = text.trim();
        if (t.length() <= max) return t;
        int sentence = -1;
        for (int i = Math.min(max, t.length()) - 1; i >= max / 2; i--) {
            char c = t.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && (i + 1 == t.length() || t.charAt(i + 1) == ' ')) {
                sentence = i;
                break;
            }
        }
        if (sentence > 0) return t.substring(0, sentence + 1);
        int word = t.lastIndexOf(' ', max - 3);
        return t.substring(0, word > max / 2 ? word : max - 3).trim() + "...";
    }

    /** {file, name, facts, overview} of a usable line, or null for a comment, a blank or a line without file or name. */
    private static String[] columns(String line) {
        if (line == null || line.trim().isEmpty() || line.trim().startsWith("#")) return null;
        int tab = line.indexOf('\t');
        if (tab <= 0) return null;
        String file = line.substring(0, tab).trim();
        String[] rest = line.substring(tab + 1).split("\t", 3);
        String name = rest[0].trim();
        if (file.isEmpty() || name.isEmpty()) return null;
        return new String[]{file, name, rest.length > 1 ? rest[1].trim() : "", rest.length > 2 ? rest[2].trim() : ""};
    }

    /** The names in {@code folder}/captions.txt, or none when the file is missing or unreadable. */
    static Map<String, String> read(File folder) {
        List<String> lines = lines(folder);
        return lines == null ? Collections.emptyMap() : parse(lines);
    }

    /** The movies in {@code folder}/captions.txt, or none when the file is missing or unreadable. */
    static Map<String, Movie> readMovies(File folder) {
        List<String> lines = lines(folder);
        return lines == null ? Collections.emptyMap() : parseMovies(lines);
    }

    private static List<String> lines(File folder) {
        File file = folder == null ? null : new File(folder, FILE_NAME);
        if (file == null || !file.isFile() || file.length() > 256 * 1024) return null;
        try {
            return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
