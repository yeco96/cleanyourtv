package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
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
 * What the kids see while kids mode has a list of allowed apps: a big tile (the app's banner) for each of those apps with the
 * time spent in it today, the day's total, and a Grown-ups tile that asks for the PIN. Back does nothing; the guard
 * (UsageGuardService) sends any other app back here.
 */
public final class KidsHomeActivity extends Activity {
    private LinearLayout tiles;
    private TextView total;
    private float density;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setBackgroundColor(0xFF101419);
        TextView title = new TextView(this);
        title.setText(R.string.kid_btn_off);
        title.setTextSize(40);
        title.setTextColor(0xFFF5B945);
        title.setGravity(Gravity.CENTER);
        page.addView(title);
        total = new TextView(this);
        total.setTextSize(22);
        total.setTextColor(0xCCFFFFFF);
        total.setGravity(Gravity.CENTER);
        page.addView(total);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.setGravity(Gravity.CENTER);
        tiles.setPadding(dp(60), dp(30), dp(60), 0);
        scroll.addView(tiles);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        setContentView(page);
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

    /** "35 min" or "1 h 5 min", the same words as the usage screen. */
    private String duration(long seconds) {
        long minutes = (seconds + 30) / 60;
        if (minutes < 60) return getString(R.string.usage_minutes, minutes);
        return getString(R.string.usage_hours_minutes, minutes / 60, minutes % 60);
    }

    private void build() {
        tiles.removeAllViews();
        PackageManager pm = getPackageManager();
        // What the kids used today, app by app, so they can see how much they have had.
        Map<String, Long> today = new HashMap<>();
        long all = 0;
        List<UsageReport.Row> rows = new UsageStore(this).since(UsageGuard.today());
        for (UsageReport.AppTotal app : UsageReport.byApp(rows, UsageReport.CHILD)) {
            today.put(app.pkg, app.seconds);
            all += app.seconds;
        }
        total.setText(getString(R.string.kid_today_total, duration(all)));
        Set<String> allowed = UsageGuard.allowed(this);
        List<String> shown = new ArrayList<>();
        for (String pkg : allowed) {
            if (launchIntent(pm, pkg) != null) shown.add(pkg);
        }
        shown.sort((a, b) -> label(pm, a).compareToIgnoreCase(label(pm, b)));
        LinearLayout first = null;
        for (String pkg : shown) {
            Intent launch = launchIntent(pm, pkg);
            Drawable banner = banner(pm, pkg, launch);
            Long seconds = today.get(pkg);
            LinearLayout tile = tile(label(pm, pkg), banner != null ? banner : icon(pm, pkg), banner != null,
                    seconds == null ? "" : duration(seconds));
            tile.setOnClickListener(v -> startActivity(launch));
            tiles.addView(tile);
            if (first == null) first = tile;
        }
        LinearLayout grownUps = tile(getString(R.string.kid_adults), null, false, "");
        grownUps.setOnClickListener(v -> startActivity(new Intent(this, KidModeActivity.class)
                .putExtra(KidModeActivity.EXTRA_MODE, KidModeActivity.MODE_MANAGE)));
        tiles.addView(grownUps);
        (first != null ? first : grownUps).requestFocus();
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
     * A big focusable tile: the app's banner (or its icon) over its name and the time spent in it today. The Grown-ups
     * tile has no picture, only a larger word.
     */
    private LinearLayout tile(String name, Drawable picture, boolean wide, String time) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackgroundResource(R.drawable.settings_choice_bg);
        tile.setPadding(dp(14), dp(14), dp(14), dp(14));
        if (picture != null) {
            ImageView image = new ImageView(this);
            image.setImageDrawable(picture);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(image, new LinearLayout.LayoutParams(wide ? dp(192) : dp(108), wide ? dp(108) : dp(108)));
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
            used.setTextColor(0xFFF5B945);
            used.setGravity(Gravity.CENTER);
            tile.addView(used);
        }
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(dp(224), dp(206));
        at.setMarginEnd(dp(18));
        tile.setLayoutParams(at);
        return tile;
    }
}
