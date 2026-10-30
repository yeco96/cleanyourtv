package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * The TMDb backdrops (config tmdb_backdrops): once a day, about 20 movie backdrops and their title, stars, year,
 * genres, runtime and overview, written into the wallpapers folder as pictures plus a captions.txt that
 * {@link WallpaperCaptions} reads for the movie panel. Based on RuckieOfficial's TmdbWallpaperFetcher.
 *
 * <p>The only servers are api.themoviedb.org and image.tmdb.org, and the only request is GET. The API key is a
 * single line in tmdb_key.txt in the launcher's files folder on the TV: it is never built into the APK or put in a
 * theme, and no URL that carries it is logged. Pictures are the 1280-pixel size, which a 1080p screen does not
 * outgrow and a 1.4 GB TV can decode; each download is capped at {@link #MAX_IMAGE_BYTES}.
 */
final class TmdbBackdrops {
    private static final String TAG = "TvHome.Tmdb";
    static final String KEY_FILE = "tmdb_key.txt";
    /**
     * The pictures live in a folder of their own, not in wallpapers/: pushing a theme clears wallpapers/, and the
     * day's films should not go with it.
     */
    static final String DIR = "tmdb";
    private static final String PREFS = "tmdb_backdrops";
    private static final String LAST_UPDATE = "last_update_ms";
    private static final long UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000;
    private static final int POPULAR = 12;
    private static final int FAMILY = 8;
    private static final int MIN_PICTURES = 5;
    private static final int MAX_PAGES = 3;
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 512 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MIN_OVERVIEW = 20;
    private static final int MAX_OVERVIEW = 220;
    private static final String API = "https://api.themoviedb.org/3";
    private static final String IMAGES = "https://image.tmdb.org/t/p/w1280";
    /** A TMDb v3 key is 32 hex digits; anything outside letters and digits never goes into a URL. */
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9]{16,64}");
    private static final Pattern LANGUAGE = Pattern.compile("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,4})?");

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean FETCHING = new AtomicBoolean(false);

    private TmdbBackdrops() { }

    /** Starts a refresh in the background when one is due, there is a key and the TV is online. */
    static void checkAndUpdate(Context context, WallpaperPlayer player, Config config) {
        if (context == null || config == null || !config.tmdbBackdrops || FETCHING.get()) return;
        Context app = context.getApplicationContext();
        File base = Config.dir(app);
        if (base == null) return;
        String key = readKey(base);
        if (key == null) {
            Log.i(TAG, "No TMDb key in " + KEY_FILE + ": nothing is fetched.");
            setStatus(app, STATUS_NO_KEY, 0);
            return;
        }
        File wallpapers = new File(base, DIR);
        boolean due = System.currentTimeMillis() - app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(LAST_UPDATE, 0) > UPDATE_INTERVAL_MS;
        if (!due && WallpaperRotation.images(wallpapers).size() >= MIN_PICTURES) return;
        String language = language(config.tmdbLanguage);
        String certification = certification(config.tmdbMaxCertification);
        EXECUTOR.execute(() -> fetch(app, base, key, language, certification, player));
    }

    /** The menu's refresh: forgets when the last one was and starts one now. False when one is already running. */
    static boolean refreshNow(Context context, WallpaperPlayer player, Config config) {
        if (FETCHING.get()) return false;
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(LAST_UPDATE, 0).apply();
        checkAndUpdate(context, player, config);
        return true;
    }

    /** The highest US rating allowed: one of G, PG, PG-13, R, NC-17; PG-13 when unset or unknown, none for "any". */
    static String certification(String configured) {
        if (configured == null) return DEFAULT_CERTIFICATION;
        String value = configured.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.equals("ANY")) return value.isEmpty() ? DEFAULT_CERTIFICATION : "";
        return CERTIFICATIONS.contains(value) ? value : DEFAULT_CERTIFICATION;
    }

    private static final String DEFAULT_CERTIFICATION = "PG-13";
    private static final Set<String> CERTIFICATIONS = new HashSet<>(java.util.Arrays.asList("G", "PG", "PG-13", "R", "NC-17"));

    /** One from each list in turn, then what is left of the longer one: popular, family, popular, family. */
    static <T> List<T> interleave(List<T> first, List<T> second) {
        List<T> mixed = new ArrayList<>(first.size() + second.size());
        for (int i = 0; i < Math.max(first.size(), second.size()); i++) {
            if (i < first.size()) mixed.add(first.get(i));
            if (i < second.size()) mixed.add(second.get(i));
        }
        return mixed;
    }

    /**
     * How the last attempt went, for the line in Launcher settings: OK (with its count and time), no key, or failed.
     * The values and the preference keys carry "tmdb:" on purpose: AppAdapter resolves drawables by name
     * (getIdentifier), so the resource shrinker keeps whatever a string constant could name, and plain "ok" or
     * "failed" kept 47 KB of unused library translations in the APK.
     */
    static final String STATUS_OK = "tmdb:ok", STATUS_NO_KEY = "tmdb:nokey", STATUS_FAILED = "tmdb:failed";

    private static void setStatus(Context context, String state, int count) {
        SharedPreferences.Editor edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("tmdb:status", state).putLong("tmdb:status_time", System.currentTimeMillis());
        if (STATUS_OK.equals(state)) edit.putInt("tmdb:status_count", count);
        edit.apply();
    }

    /** The last attempt's state, or null before the first one. */
    static String status(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tmdb:status", null);
    }

    /** Pictures the last good download brought. */
    static int statusCount(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("tmdb:status_count", 0);
    }

    /** When the last attempt ended. */
    static long statusTime(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("tmdb:status_time", 0);
    }

    /** The key in {@code tmdb_key.txt}, or null when there is no such file or it does not look like a key. */
    static String readKey(File base) {
        File file = new File(base, KEY_FILE);
        if (!file.isFile() || file.length() > 4096) return null;
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            String key = lines.isEmpty() ? "" : lines.get(0).trim();
            return KEY.matcher(key).matches() ? key : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The configured language when it is well formed, else the TV's own, else English. */
    static String language(String configured) {
        if (configured != null && LANGUAGE.matcher(configured).matches()) return configured;
        Locale locale = Locale.getDefault();
        String tag = locale.getCountry().isEmpty() ? locale.getLanguage() : locale.getLanguage() + "-" + locale.getCountry();
        return LANGUAGE.matcher(tag).matches() ? tag : "en-US";
    }

    private static void fetch(Context context, File base, String key, String language, String certification,
                              WallpaperPlayer player) {
        if (!FETCHING.compareAndSet(false, true)) return;
        File staging = new File(base, "tmdb_staging");
        String outcome = STATUS_FAILED;
        int delivered = 0;
        try {
            if (!online(context)) {
                Log.i(TAG, "Offline: no fetch.");
                return;
            }
            Set<Integer> seen = new HashSet<>();
            // The rating filter works on discover, so with one the popular list is discover sorted by popularity.
            String rated = certification.isEmpty() ? "" : "&certification_country=US&certification.lte=" + certification;
            String popularUrl = certification.isEmpty()
                    ? API + "/movie/popular?api_key=" + key + "&language=" + language
                    : API + "/discover/movie?api_key=" + key + "&language=" + language
                            + "&include_adult=false&sort_by=popularity.desc&vote_count.gte=200&vote_average.gte=6" + rated;
            List<JSONObject> popular = pick(popularUrl, POPULAR, seen);
            List<JSONObject> family = pick(API + "/discover/movie?api_key=" + key + "&with_companies=2%7C3&language="
                    + language + "&include_adult=false&vote_count.gte=200&vote_average.gte=6" + rated, FAMILY, seen);
            // One of each in turn, so the sequence is varied (not twelve popular films and then eight Disney ones).
            List<JSONObject> movies = interleave(popular, family);
            if (movies.isEmpty()) {
                Log.w(TAG, "TMDb returned no movies (is the key valid?).");
                return;
            }
            deleteTree(staging);
            if (!staging.mkdirs()) return;
            List<String> captions = new ArrayList<>();
            int count = 0;
            for (JSONObject movie : movies) {
                int id = movie.optInt("id");
                String file = String.format(Locale.ROOT, "%02d_%d.jpg", count + 1, id);
                if (!download(IMAGES + movie.optString("backdrop_path"), new File(staging, file))) continue;
                count++;
                captions.add(file + "\t" + clean(movie.optString("title")) + "\t" + facts(movie, details(id, key, language))
                        + "\t" + overview(movie.optString("overview")));
            }
            if (captions.size() < MIN_PICTURES) {
                Log.w(TAG, "Only " + captions.size() + " pictures came down: the old ones stay.");
                return;
            }
            write(new File(staging, WallpaperCaptions.FILE_NAME), captions);
            File wallpapers = new File(base, DIR);
            if (!wallpapers.isDirectory() && !wallpapers.mkdirs()) return;
            File[] old = wallpapers.listFiles();
            if (old != null) for (File f : old) if (f.isFile()) f.delete();
            File[] fresh = staging.listFiles();
            if (fresh != null) for (File f : fresh) f.renameTo(new File(wallpapers, f.getName()));
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(LAST_UPDATE, System.currentTimeMillis()).apply();
            Log.i(TAG, "TMDb backdrops updated: " + captions.size());
            outcome = STATUS_OK;
            delivered = captions.size();
            if (player != null) player.reload(wallpapers);
        } catch (Exception e) {
            Log.w(TAG, "TMDb fetch failed: " + e.getClass().getSimpleName());
        } finally {
            setStatus(context, outcome, delivered);
            deleteTree(staging);
            FETCHING.set(false);
        }
    }

    /** Up to {@code want} movies from the first pages of this list that have a backdrop and a real overview. */
    private static List<JSONObject> pick(String url, int want, Set<Integer> seen) {
        List<JSONObject> picked = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES && picked.size() < want; page++) {
            JSONObject json = json(url + "&page=" + page);
            JSONArray results = json == null ? null : json.optJSONArray("results");
            if (results == null) continue;
            for (int i = 0; i < results.length() && picked.size() < want; i++) {
                JSONObject movie = results.optJSONObject(i);
                if (movie == null) continue;
                int id = movie.optInt("id");
                String backdrop = movie.optString("backdrop_path");
                boolean usable = id > 0 && backdrop.startsWith("/") && backdrop.matches("/[A-Za-z0-9._-]+")
                        && movie.optString("overview").trim().length() >= MIN_OVERVIEW
                        && !movie.optString("title").trim().isEmpty();
                if (usable && seen.add(id)) picked.add(movie);
            }
        }
        return picked;
    }

    private static JSONObject details(int id, String key, String language) {
        return json(API + "/movie/" + id + "?api_key=" + key + "&language=" + language);
    }

    /** "★★★★☆ 7.8  •  2021  •  Action, Drama  •  2h 35m": the part before the first bullet is the stars. */
    private static String facts(JSONObject movie, JSONObject details) {
        double vote = movie.optDouble("vote_average", 0);
        int stars = Math.max(0, Math.min(5, (int) Math.round(vote / 2)));
        StringBuilder stripe = new StringBuilder();
        for (int i = 0; i < 5; i++) stripe.append(i < stars ? '★' : '☆');
        List<String> parts = new ArrayList<>();
        parts.add(String.format(Locale.ROOT, "%s %.1f", stripe, vote));
        String date = movie.optString("release_date");
        if (date.length() >= 4) parts.add(date.substring(0, 4));
        if (details != null) {
            JSONArray genres = details.optJSONArray("genres");
            List<String> names = new ArrayList<>();
            for (int i = 0; genres != null && i < genres.length() && names.size() < 3; i++) {
                JSONObject genre = genres.optJSONObject(i);
                String name = genre == null ? "" : clean(genre.optString("name"));
                if (!name.isEmpty()) names.add(name);
            }
            if (!names.isEmpty()) parts.add(String.join(", ", names));
            int minutes = details.optInt("runtime");
            if (minutes >= 60) parts.add((minutes / 60) + "h " + (minutes % 60) + "m");
            else if (minutes > 0) parts.add(minutes + "m");
        }
        return String.join("  •  ", parts);
    }

    private static String overview(String text) {
        String t = clean(text);
        if (t.length() <= MAX_OVERVIEW) return t;
        int cut = t.lastIndexOf(' ', MAX_OVERVIEW - 3);
        return t.substring(0, cut > MAX_OVERVIEW / 2 ? cut : MAX_OVERVIEW - 3).trim() + "...";
    }

    /** One line, no tabs: the captions file is tab separated, one movie per line. */
    private static String clean(String text) {
        return text == null ? "" : text.replaceAll("[\\t\\r\\n]+", " ").trim();
    }

    private static boolean online(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm == null ? null : cm.getActiveNetwork();
        NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /** A GET answered 200, read to the end up to {@code limit} bytes; null otherwise. Nothing here logs the URL. */
    private static byte[] get(String address, int limit) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            if (connection.getResponseCode() != 200) return null;
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    if (out.size() > limit) return null;
                }
                return out.toByteArray();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static JSONObject json(String address) {
        byte[] body = get(address, MAX_JSON_BYTES);
        try {
            return body == null ? null : new JSONObject(new String(body, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean download(String address, File to) {
        byte[] body = get(address, MAX_IMAGE_BYTES);
        if (body == null || body.length == 0) return false;
        try (FileOutputStream out = new FileOutputStream(to)) {
            out.write(body);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void write(File file, List<String> lines) throws Exception {
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (String line : lines) {
                out.write(line);
                out.newLine();
            }
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.isDirectory() ? file.listFiles() : null;
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
