package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    private final FrameLayout root;
    private final ImageView image;
    private final int backgroundColor;
    private final List<File> files;
    private final long interval;
    private final String order;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final int width;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int height;
    /** wallpaper_dim as an ARGB black, painted into each picture once instead of a full-screen shade view. */
    private final int dimColor;
    /** The picture on screen, released once the next one has been drawn. */
    private Bitmap current;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private String displayed = "";
    /** wallpaper_captions: the shown picture's name from wallpapers/captions.txt; null when off or there are none. */
    private final TextView caption;
    private final Map<String, String> names;
    /** tmdb_backdrops: the movie panel (title, stars and facts, overview) in place of the one-line caption. */
    private final boolean tmdb;
    private final Config config;
    private TextView movieTitle, movieFacts, movieOverview;
    private LinearLayout moviePanel;
    /** tmdb_backdrops: the cinematic scrims (left, bottom and top gradients in one layer) and the dim over the picture. */
    private View scrim, dim;
    /** How dark the picture is while the focus is on the dock, and while it is down in the app rows. */
    private static final float DIM_DOCK = 0.25f, DIM_GRID = 0.75f;
    private Map<String, WallpaperCaptions.Movie> movies = Collections.emptyMap();
    private boolean panelVisible = true;
    /** wallpaper_moods: the collection's groups by time of day; empty rotates through all of it. */
    private final Map<String, Set<String>> moods;
    /** config.xml latitude and longitude for the sun's times, and today's {sunrise, sunset} once worked out. */
    private final String latitude, longitude;
    private LocalDate sunDay;
    private int[] sun;

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        this.config = config;
        tmdb = config.tmdbBackdrops;
        backgroundColor = config.backgroundColor;
        files = new ArrayList<>(config.wallpapers);
        // TMDb backdrops turn over on their own timer, in seconds (tmdb_rotation_seconds); a theme's own wallpapers
        // keep the minutes, checked once a minute by Home.
        interval = config.tmdbBackdrops ? config.tmdbRotationSeconds * 1000L : config.wallpaperRotationMinutes * 60_000L;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        // The dim is painted into the picture (decode), so no full-screen shade layer is drawn on every frame.
        // With tmdb_backdrops the dim is a layer of its own (it changes with the focus), not painted into the picture.
        dimColor = config.wallpaperDim > 0 && !config.tmdbBackdrops ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        order = config.wallpaperOrder;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        moods = config.wallpaperMoods;
        latitude = moods.isEmpty() ? "" : context.getString(R.string.latitude);
        longitude = moods.isEmpty() ? "" : context.getString(R.string.longitude);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        String collection = collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").remove("shown").apply();
        }
        // The shuffle's deck survives a restart of Home, so a picture does not come back because the launcher did.
        shownThisRound.addAll(preferences.getStringSet("shown", Collections.emptySet()));
        Set<String> present = new java.util.HashSet<>();
        for (File file : files) present.add(file.getName());
        shownThisRound.retainAll(present);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        names = config.wallpaperCaptions && config.wallpaperEnabled && !files.isEmpty()
                ? WallpaperCaptions.read(files.get(0).getParentFile()) : Collections.emptyMap();
        caption = names.isEmpty() || tmdb ? null : makeCaption(context, root);
        if (tmdb) {
            if (!files.isEmpty()) movies = WallpaperCaptions.readMovies(files.get(0).getParentFile());
            scrim = makeScrim(context);
            leaving = new ImageView(context);
            leaving.setScaleType(ImageView.ScaleType.CENTER_CROP);
            leaving.setAlpha(0f);
            leaving.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            dim = new View(context);
            dim.setBackgroundColor(0xFF000000);
            dim.setAlpha(DIM_DOCK);
            dim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            makeMoviePanel(context, root);
        }
        if (config.wallpaperEnabled && !files.isEmpty()) {
            attach();
            showFirst();
        }
    }

    /** The pictures this round of a shuffle has shown already: every picture comes once before any comes back. */
    private final Set<String> shownThisRound = new java.util.HashSet<>();

    /** The next picture: the sorted order for wallpaper_order sequential, else a shuffle that does not repeat. */
    private File chooseNext(List<File> playable, String previous) {
        if (WallpaperRotation.ORDER_SEQUENTIAL.equals(order)) return WallpaperRotation.next(playable, previous);
        File picked = WallpaperRotation.pickFresh(playable, shownThisRound, previous, random);
        if (picked != null) preferences.edit().putStringSet("shown", new java.util.HashSet<>(shownThisRound)).apply();
        return picked;
    }

    /**
     * The picture goes under everything; with tmdb_backdrops the picture that is leaving (the cross-fade) and the
     * scrims and the dim go right over it, in that order.
     */
    private void attach() {
        if (image.getParent() != null) return;
        ViewGroup.LayoutParams fill = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(image, 0, fill);
        if (tmdb) {
            root.addView(leaving, 1, fill);
            root.addView(scrim, 2, fill);
            root.addView(dim, 3, fill);
        }
    }

    /**
     * The cinematic vignette: dark where the text sits (a gradient from the left edge, one from the bottom, a light
     * one from the top), clear over the rest of the picture. One static layer, drawn once into a texture, so a
     * bright backdrop never costs the text its contrast and the picture costs nothing per frame.
     */
    private View makeScrim(Context context) {
        GradientDrawable left = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xE6000000, 0x73000000, 0x00000000});
        GradientDrawable bottom = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xD9000000, 0x73000000, 0x00000000});
        GradientDrawable top = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x73000000, 0x00000000});
        LayerDrawable layers = new LayerDrawable(new GradientDrawable[]{left, bottom, top});
        layers.setLayerGravity(0, Gravity.START);
        layers.setLayerSize(0, Math.round(width * 0.62f), height);
        layers.setLayerGravity(1, Gravity.BOTTOM);
        layers.setLayerSize(1, width, Math.round(height * 0.55f));
        layers.setLayerGravity(2, Gravity.TOP);
        layers.setLayerSize(2, width, Math.round(height * 0.24f));
        View view = new View(context);
        view.setBackground(layers);
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        List<File> playable = playable();
        File first = WallpaperRotation.find(playable, previous);
        if (first == null) first = chooseNext(playable, previous);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                show(bitmap);
                // The picture covers the whole screen: the root's plain colour under it is overdraw.
                root.setBackground(null);
                displayed = first.getName();
                showCaption(false);
                applyContrast();
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            playable = playable();
            first = chooseNext(playable, previous);
        }
    }

    /** tmdb_backdrops: the next picture every {@link #interval} while Home is on screen, and nothing while it is not. */
    private final Runnable turnOver = new Runnable() {
        @Override
        public void run() {
            if (!active || closed) return;
            update(true);
            root.postDelayed(this, interval);
        }
    };

    void start() {
        active = true;
        update();
        if (tmdb) {
            root.removeCallbacks(turnOver);
            root.postDelayed(turnOver, interval);
            TmdbBackdrops.checkAndUpdate(root.getContext(), this, config);
        }
    }

    /** The folder holds new pictures (the TMDb refresh swapped them in): play from them, with their movie details. */
    void reload(File wallpapers) {
        List<File> fresh = WallpaperRotation.images(wallpapers);
        if (fresh.isEmpty()) return;
        Map<String, WallpaperCaptions.Movie> freshMovies = WallpaperCaptions.readMovies(wallpapers);
        root.post(() -> {
            if (closed) return;
            files.clear();
            files.addAll(fresh);
            // Pictures that are gone leave the deck; the ones that stay keep their place in it.
            Set<String> kept = new java.util.HashSet<>();
            for (File file : fresh) kept.add(file.getName());
            shownThisRound.retainAll(kept);
            movies = freshMovies;
            attach();
            preferences.edit().remove("image").remove("changed").apply();
            showFirst();
        });
    }

    void stop() {
        active = false;
        root.removeCallbacks(turnOver);
    }

    /** Drops the wallpaper texture when Home is hidden and Android asks the process to free memory. */
    void releaseForMemoryPressure() {
        if (current == null) return;
        image.setImageDrawable(null);
        if (leaving != null) leaving.setImageDrawable(null);
        // The renderer may still have a queued frame that references this hardware bitmap. Drop our references and
        // let Android reclaim its native buffer after rendering releases it instead of recycling it mid-frame.
        current = null;
        displayed = "";
        root.setBackgroundColor(backgroundColor);
    }

    /** The pictures the rotation picks from now: wallpaper_moods' group for the time of day, or the whole collection. */
    private List<File> playable() {
        if (moods.isEmpty()) return files;
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(sunDay)) {
            sunDay = now.toLocalDate();
            sun = WallpaperMood.sun(sunDay, latitude, longitude, ZoneId.systemDefault());
        }
        return WallpaperMood.select(files, moods, WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1]));
    }

    void close() {
        active = false;
        closed = true;
        root.removeCallbacks(turnOver);
        decoder.shutdownNow();
    }

    void update() {
        update(false);
    }

    /**
     * {@code turnOver} forces the next picture; otherwise it comes when the interval is due. With tmdb_backdrops the
     * interval is the turnOver timer's, so Home's minute check only keeps the current picture and never turns it over.
     */
    private void update(boolean turnOver) {
        if (!active || closed || loading || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        // A picture outside the time of day's group (the group changed) is replaced at once, not after the interval.
        List<File> playable = playable();
        File selected = WallpaperRotation.find(playable, previous);
        long changed = preferences.getLong("changed", 0);
        boolean due = turnOver || (!tmdb && WallpaperRotation.due(changed, System.currentTimeMillis(), interval));
        if (selected == null || due) {
            selected = chooseNext(playable, previous);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            root.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                show(bitmap);
                displayed = next.getName();
                showCaption(true);
                applyContrast();
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
            });
        });
    }

    /**
     * Small light text under the top-right buttons, above everything else and never focusable. It sits where the
     * top bar ends in every layout, so it does not cover the clock, the stats line or the tiles.
     */
    private static TextView makeCaption(Context context, FrameLayout root) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(0xD9FFFFFF);
        text.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Math.round(520 * dp));
        text.setFocusable(false);
        text.setAlpha(0f);
        try {
            text.setTypeface(UiFont.get(context, R.string.font_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        params.topMargin = Math.round(110 * dp);
        params.setMarginEnd(Math.round(62 * dp));
        root.addView(text, params);
        return text;
    }

    /** The displayed picture's name, faded across a change; a picture without a name hides the caption. */
    private void showCaption(boolean fade) {
        if (tmdb) {
            showMovie(fade);
            return;
        }
        if (caption == null) return;
        String name = names.get(displayed);
        caption.animate().cancel();
        if (name == null) {
            caption.setAlpha(0f);
            return;
        }
        caption.setContentDescription(name);
        if (!fade || caption.getAlpha() == 0f) {
            caption.setText(name);
            caption.setAlpha(1f);
            return;
        }
        caption.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            caption.setText(name);
            caption.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    /**
     * The movie panel of tmdb_backdrops: the title (up to two lines), the stars with the year, genres and runtime,
     * and the overview. Top left, under the clock, above everything else and never focusable.
     */
    private void makeMoviePanel(Context context, FrameLayout parent) {
        float dp = context.getResources().getDisplayMetrics().density;
        moviePanel = new LinearLayout(context);
        moviePanel.setOrientation(LinearLayout.VERTICAL);
        moviePanel.setFocusable(false);
        moviePanel.setAlpha(0f);
        movieTitle = movieText(context, 32, 0xFFFFFFFF, R.string.font_medium);
        movieTitle.setMaxLines(2);
        movieTitle.setMaxWidth(Math.round(width * 0.62f));
        movieTitle.setLineSpacing(2 * dp, 1.1f);
        movieTitle.setPadding(0, 0, Math.round(16 * dp), Math.round(4 * dp));
        movieFacts = movieText(context, 15, 0xFFFFD700, R.string.font_medium);
        movieFacts.setSingleLine(true);
        movieFacts.setMaxWidth(Math.round(width * 0.62f));
        movieFacts.setPadding(0, Math.round(4 * dp), 0, Math.round(6 * dp));
        // The overview stays inside the left scrim: under half the screen, two lines, near-opaque with a firm shadow.
        movieOverview = movieText(context, 14, 0xF2F1F5F9, R.string.font_regular);
        movieOverview.setShadowLayer(12 * dp, 0, dp, 0xFF000000);
        movieOverview.setMaxLines(2);
        movieOverview.setMaxWidth(Math.round(width * 0.45f));
        movieOverview.setLineSpacing(2 * dp, 1.15f);
        moviePanel.addView(movieTitle);
        moviePanel.addView(movieFacts);
        moviePanel.addView(movieOverview);
        // Anchored by its bottom, just above the dock, so a title of two lines grows upward instead of into the dock;
        // until Home says where the dock is (anchorPanel) it sits at mid-height.
        panelParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        panelParams.bottomMargin = Math.round(height * 0.5f);
        panelParams.setMarginStart(Math.round(60 * dp));
        parent.addView(moviePanel, panelParams);
    }

    private FrameLayout.LayoutParams panelParams;

    /** Home reports where the dock band starts (y, in the root's pixels): the panel's bottom sits a little above it. */
    void anchorPanel(int dockTopY) {
        if (moviePanel == null || panelParams == null) return;
        float dp = root.getContext().getResources().getDisplayMetrics().density;
        int rootHeight = root.getHeight() > 0 ? root.getHeight() : height;
        int bottom = Math.max(0, rootHeight - dockTopY + Math.round(14 * dp));
        if (bottom == panelParams.bottomMargin) return;
        panelParams.bottomMargin = bottom;
        moviePanel.setLayoutParams(panelParams);
    }

    private static TextView movieText(Context context, int sp, int color, int font) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        text.setTextColor(color);
        text.setShadowLayer(8 * dp, 0, dp, 0xE6000000);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setFocusable(false);
        try {
            text.setTypeface(UiFont.get(context, font));
        } catch (Exception ignored) {
            // the system font is fine
        }
        return text;
    }

    /** The displayed picture's movie, faded across a change; a picture with no movie leaves the panel empty. */
    private void showMovie(boolean fade) {
        if (moviePanel == null) return;
        WallpaperCaptions.Movie movie = movies.get(displayed);
        if (movie == null) {
            moviePanel.setAlpha(0f);
            return;
        }
        Runnable fill = () -> {
            movieTitle.setText(movie.title);
            movieFacts.setText(stars(movie.facts));
            movieFacts.setVisibility(movie.facts.isEmpty() ? View.GONE : View.VISIBLE);
            String overview = WallpaperCaptions.shorten(movie.overview, 140);
            movieOverview.setText(overview);
            movieOverview.setVisibility(overview.isEmpty() ? View.GONE : View.VISIBLE);
            moviePanel.setContentDescription(movie.title);
        };
        moviePanel.animate().cancel();
        if (!fade || moviePanel.getAlpha() == 0f) {
            fill.run();
            moviePanel.setAlpha(panelVisible ? 1f : 0f);
            return;
        }
        moviePanel.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            fill.run();
            moviePanel.animate().alpha(panelVisible ? 1f : 0f).setDuration(350).start();
        }).start();
    }

    /** The stars (the text before the first bullet) in gold, the rest in the overview's soft white. */
    private static CharSequence stars(String facts) {
        int bullet = facts.indexOf('•');
        if (bullet <= 0) return facts;
        SpannableString span = new SpannableString(facts);
        span.setSpan(new ForegroundColorSpan(0xFFFFD700), 0, bullet, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(0xD9E2E8F0), bullet, facts.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    /**
     * The panel glides up and fades out while the focus is below the dock, and comes back with it. Only with
     * tmdb_backdrops.
     */
    void setPanelVisible(boolean visible, boolean animate) {
        if (moviePanel == null || visible == panelVisible) return;
        panelVisible = visible;
        float dp = root.getContext().getResources().getDisplayMetrics().density;
        float y = visible ? 0f : -90f * dp;
        float alpha = visible && movies.get(displayed) != null ? 1f : 0f;
        // Down in the app rows the picture goes dark, so the tiles read over any backdrop; back on the dock it returns.
        float dimAlpha = visible ? dockDimAlpha : DIM_GRID;
        moviePanel.animate().cancel();
        dim.animate().cancel();
        if (!animate) {
            moviePanel.setTranslationY(y);
            moviePanel.setAlpha(alpha);
            dim.setAlpha(dimAlpha);
            return;
        }
        dim.animate().alpha(dimAlpha).setDuration(visible ? 320 : 240).start();
        moviePanel.animate().translationY(y).alpha(alpha).setDuration(visible ? 320 : 240)
                .setInterpolator(visible ? new DecelerateInterpolator() : new AccelerateInterpolator()).start();
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getName()).append(':').append(file.length()).append(';');
        return key.toString();
    }

    /** Puts a picture on screen and releases the previous one after a frame has drawn the new one. */
    private void show(Bitmap bitmap) {
        Bitmap old = current;
        current = bitmap;
        // The wallpaper covers the whole root; avoid drawing the fallback colour under it.
        root.setBackground(null);
        if (tmdb && leaving != null && old != null && old != bitmap && image.getDrawable() != null
                && image.getParent() != null) {
            // The film changes with a cross-fade: the one leaving stays on a layer above and melts away, and is
            // released once it is gone. Two backdrops are in memory for that moment (about 7 MB).
            leaving.animate().cancel();
            leaving.setImageDrawable(image.getDrawable());
            leaving.setAlpha(1f);
            image.setImageBitmap(bitmap);
            leaving.animate().alpha(0f).setDuration(CROSSFADE_MS).withEndAction(() -> leaving.setImageDrawable(null)).start();
            image.postDelayed(old::recycle, CROSSFADE_MS + 400);
            return;
        }
        image.setImageBitmap(bitmap);
        if (old != null && old != bitmap) image.postDelayed(old::recycle, 500);
    }

    /** How long a film takes to melt into the next (tmdb_backdrops). */
    private static final long CROSSFADE_MS = 700;
    /** The leaving film's layer, above the picture and below the scrims. */
    private ImageView leaving;
    /** Mean brightness of each picture's text side, measured when it is decoded. */
    private final Map<String, Float> brightness = new java.util.concurrent.ConcurrentHashMap<>();
    /** The dim over the picture while the focus is on the dock: lighter for a dark film, darker for a bright one. */
    private float dockDimAlpha = DIM_DOCK;

    /**
     * Sets the scrim and the dim for the picture now on screen from how bright its text side is: a dark film keeps its
     * colour, a bright one gets the full scrim and a darker picture. Fades with the cross-fade.
     */
    private void applyContrast() {
        if (!tmdb || scrim == null) return;
        Float measured = brightness.get(displayed);
        float luminance = measured == null ? 0.4f : measured;
        dockDimAlpha = BackdropContrast.dockDim(luminance);
        scrim.animate().cancel();
        scrim.animate().alpha(BackdropContrast.scrim(luminance)).setDuration(CROSSFADE_MS).start();
        if (panelVisible) {
            dim.animate().cancel();
            dim.animate().alpha(dockDimAlpha).setDuration(CROSSFADE_MS).start();
        }
    }

    /** Mean brightness of the left 55% of the picture, where the text goes; sampled, not every pixel. */
    private static float textSideBrightness(Bitmap bitmap) {
        int columns = 28, rows = 16;
        int[] sample = new int[columns * rows];
        int count = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                int x = (int) ((c + 0.5f) / columns * 0.55f * bitmap.getWidth());
                int y = (int) ((r + 0.5f) / rows * bitmap.getHeight());
                sample[count++] = bitmap.getPixel(Math.min(x, bitmap.getWidth() - 1), Math.min(y, bitmap.getHeight() - 1));
            }
        }
        return BackdropContrast.luminance(sample, count);
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = bitmapConfig;
            options.inMutable = dimColor != 0;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            if (dimColor != 0) new Canvas(decoded).drawColor(dimColor);
            if (tmdb) brightness.put(file.getName(), textSideBrightness(decoded));
            // One copy in GPU memory instead of a software bitmap plus its texture; the decode stays off the UI
            // thread for rotations, and Home's return no longer uploads 4-8 MB to the GPU.
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
