package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the kids see while kids mode has a list of allowed apps: a night sky with a greeting, a bar that shows how much of
 * the day's time is used, and a glass dock (like the grown-ups' one) with a tile for each allowed app and the time spent in
 * it today, and a Grown-ups tile that asks for the PIN. Back does nothing; the guard (UsageGuardService) sends any other
 * app back here. With the time used up, only the Grown-ups tile is left.
 */
public final class KidsHomeActivity extends Activity {
    private static final int GOLD = 0xFFF5B945;

    private LinearLayout tiles;
    private TextView total;
    private LinearLayout barTrack;
    private View barFill;
    private float density;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.addView(new KidsBackground(this), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(64), dp(48), dp(64), dp(44));
        TextView hello = new TextView(this);
        hello.setText(R.string.kid_hello);
        hello.setTextSize(48);
        hello.setTypeface(Typeface.DEFAULT_BOLD);
        hello.setTextColor(Color.WHITE);
        hello.setShadowLayer(8 * density, 0, 2 * density, 0x66000000);
        column.addView(hello);
        total = new TextView(this);
        total.setTextSize(24);
        total.setTextColor(0xFFFFE9A8);
        column.addView(total);
        barTrack = new LinearLayout(this);
        barTrack.setBackground(rounded(0x33FFFFFF, 0, 0, 10));
        barFill = new View(this);
        barTrack.addView(barFill, new LinearLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams trackAt = new LinearLayout.LayoutParams(dp(520), dp(18));
        trackAt.topMargin = dp(14);
        column.addView(barTrack, trackAt);
        column.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));   // pushes the dock to the bottom

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.setGravity(Gravity.CENTER);
        tiles.setPadding(dp(20), dp(30), dp(20), dp(26));
        scroll.addView(tiles);
        FrameLayout dock = new FrameLayout(this);
        dock.setBackground(rounded(0x33FFFFFF, 0x66FFFFFF, 2, 30));
        dock.addView(scroll);
        column.addView(dock, new LinearLayout.LayoutParams(-1, -2));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!UsageGuard.guarding(this)) {   // the mode was turned off (or its list emptied): back to the normal Home
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            return;
        }
        UsageGuard.ensureService(this);
        UsageTracker.update(getApplicationContext(), UsageReport.CHILD);
        UsageGuard.checkNotice(this);
        build();
    }

    @Override
    public void onBackPressed() {
        // Nothing: the kids' screen is left only through the Grown-ups tile and the PIN.
    }

    private int dp(int value) {
        return Math.round(value * density);
    }

    private GradientDrawable rounded(int fill, int stroke, int strokeDp, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusDp * density);
        if (strokeDp > 0) d.setStroke(dp(strokeDp), stroke);
        return d;
    }

    /** "35 min" or "1 h 5 min", the same words as the usage screen; whole hours read "1 h". */
    private String duration(long seconds) {
        long minutes = (seconds + 30) / 60;
        if (minutes < 60) return getString(R.string.usage_minutes, minutes);
        if (minutes % 60 == 0) return getString(R.string.kid_hours, minutes / 60);
        return getString(R.string.usage_hours_minutes, minutes / 60, minutes % 60);
    }

    private void build() {
        tiles.removeAllViews();
        PackageManager pm = getPackageManager();
        // What the kids used today, app by app, so they can see how much they have had.
        Map<String, Long> today = new HashMap<>();
        long all = 0;
        List<UsageReport.Row> rows = UsageStore.of(this).since(UsageGuard.today());
        for (UsageReport.AppTotal app : UsageReport.byApp(rows, UsageReport.CHILD)) {
            today.put(app.pkg, app.seconds);
            all += app.seconds;
        }
        boolean up = UsageGuard.timeUp(this);
        int limit = UsageGuard.limitToday(this);
        int extra = UsageGuard.extraMinutes(this);
        total.setText(up ? getString(R.string.kid_time_up) : limit <= 0
                ? getString(R.string.kid_today_total, duration(all))
                : extra > 0 ? getString(R.string.kid_today_of_extra, duration(all), duration(limit * 60L), duration(extra * 60L))
                : getString(R.string.kid_today_of, duration(all), duration(limit * 60L)));
        showBar(all, limit, extra);
        Set<String> allowed = UsageGuard.allowed(this);
        List<String> shown = new ArrayList<>();
        for (String pkg : allowed) {
            if (!up && launchIntent(pm, pkg) != null) shown.add(pkg);   // time is up: no app tiles, only Grown-ups
        }
        shown.sort((a, b) -> label(pm, a).compareToIgnoreCase(label(pm, b)));
        View first = null;
        for (String pkg : shown) {
            Intent launch = launchIntent(pm, pkg);
            Drawable banner = banner(pm, pkg, launch);
            Long seconds = today.get(pkg);
            View tile = tile(label(pm, pkg), banner != null ? banner : icon(pm, pkg), banner != null,
                    seconds == null ? "" : duration(seconds));
            tile.setOnClickListener(v -> startActivity(launch));
            tiles.addView(tile);
            if (first == null) first = tile;
        }
        View grownUps = tile(getString(R.string.kid_adults), null, false, "");
        grownUps.setOnClickListener(v -> startActivity(new Intent(this, KidModeActivity.class)
                .putExtra(KidModeActivity.EXTRA_MODE, KidModeActivity.MODE_MANAGE)));
        tiles.addView(grownUps);
        (first != null ? first : grownUps).requestFocus();
    }

    /** The bar fills as the time is used: green, then yellow, then red; hidden when there is no limit. */
    private void showBar(long usedSeconds, int limit, int extra) {
        if (limit <= 0) {
            barTrack.setVisibility(View.GONE);
            return;
        }
        barTrack.setVisibility(View.VISIBLE);
        float used = Math.min(1f, usedSeconds / ((limit + extra) * 60f));
        int color = used < 0.7f ? 0xFF6BD66B : used < 0.9f ? GOLD : 0xFFFF6B5E;
        barFill.setBackground(rounded(color, 0, 0, 10));
        LinearLayout.LayoutParams at = (LinearLayout.LayoutParams) barFill.getLayoutParams();
        barTrack.post(() -> {
            at.width = Math.max(dp(18), Math.round(barTrack.getWidth() * used));
            barFill.setLayoutParams(at);
        });
    }

    private Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    /** The app's TV banner (the wide picture its own launcher entry shows), or null when it has none. */
    private Drawable banner(PackageManager pm, String pkg, Intent launch) {
        try {
            ComponentName component = launch == null ? null : launch.getComponent();
            Drawable d = component == null ? null : pm.getActivityBanner(component);
            return d != null ? d : pm.getApplicationBanner(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private Drawable icon(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return pm.getDefaultActivityIcon();
        }
    }

    /**
     * A focusable tile of the dock: the app's banner with rounded corners (or its icon), its name and the time spent in it
     * today. The focused one gets a gold edge. The Grown-ups tile has no picture, only a larger word.
     */
    private View tile(String name, Drawable picture, boolean wide, String time) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable idle = rounded(0x22FFFFFF, 0x00000000, 3, 18);
        GradientDrawable focused = rounded(0x55FFFFFF, GOLD, 4, 18);
        tile.setBackground(idle);
        tile.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? focused : idle);
        });
        if (picture != null) {
            ImageView image = new ImageView(this);
            image.setImageDrawable(picture);
            image.setScaleType(wide ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            if (wide) {
                image.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 12 * density);
                    }
                });
                image.setClipToOutline(true);
            }
            tile.addView(image, new LinearLayout.LayoutParams(wide ? dp(176) : dp(99), dp(99)));
        }
        TextView label = new TextView(this);
        label.setText(name);
        label.setTextSize(picture != null ? 20 : 26);
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelAt = new LinearLayout.LayoutParams(-2, -2);
        labelAt.topMargin = dp(8);
        tile.addView(label, labelAt);
        if (!time.isEmpty()) {
            TextView used = new TextView(this);
            used.setText(time);
            used.setTextSize(18);
            used.setTextColor(GOLD);
            used.setGravity(Gravity.CENTER);
            tile.addView(used);
        }
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(dp(200), dp(190));
        at.setMarginEnd(dp(16));
        tile.setLayoutParams(at);
        return tile;
    }
}
