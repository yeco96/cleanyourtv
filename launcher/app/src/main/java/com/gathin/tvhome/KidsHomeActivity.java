package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What the kids see while kids mode has a list of allowed apps: big tiles for those apps and a Grown-ups tile that asks
 * for the PIN. Back does nothing; the guard (UsageGuardService) sends any other app back here.
 */
public final class KidsHomeActivity extends Activity {
    private LinearLayout tiles;
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
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.setGravity(Gravity.CENTER);
        tiles.setPadding(dp(60), dp(36), dp(60), 0);
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

    private void build() {
        tiles.removeAllViews();
        PackageManager pm = getPackageManager();
        Set<String> allowed = UsageGuard.allowed(this);
        List<String> shown = new ArrayList<>();
        for (String pkg : allowed) {
            if (launchIntent(pm, pkg) != null) shown.add(pkg);
        }
        shown.sort((a, b) -> label(pm, a).compareToIgnoreCase(label(pm, b)));
        LinearLayout first = null;
        for (String pkg : shown) {
            LinearLayout tile = tile(label(pm, pkg), icon(pm, pkg));
            tile.setOnClickListener(v -> startActivity(launchIntent(pm, pkg)));
            tiles.addView(tile);
            if (first == null) first = tile;
        }
        LinearLayout grownUps = tile(getString(R.string.kid_adults), null);
        grownUps.setOnClickListener(v -> startActivity(new Intent(this, KidModeActivity.class)
                .putExtra(KidModeActivity.EXTRA_MODE, KidModeActivity.MODE_MANAGE)));
        tiles.addView(grownUps);
        (first != null ? first : grownUps).requestFocus();
    }

    private Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    private String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private android.graphics.drawable.Drawable icon(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return pm.getDefaultActivityIcon();
        }
    }

    /** A big focusable tile: the app's icon over its name; the Grown-ups tile has no icon, only a larger word. */
    private LinearLayout tile(String name, android.graphics.drawable.Drawable icon) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackgroundResource(R.drawable.settings_choice_bg);
        tile.setPadding(dp(16), dp(16), dp(16), dp(16));
        if (icon != null) {
            ImageView image = new ImageView(this);
            image.setImageDrawable(icon);
            tile.addView(image, new LinearLayout.LayoutParams(dp(120), dp(120)));
        }
        TextView label = new TextView(this);
        label.setText(name);
        label.setTextSize(icon != null ? 20 : 26);
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelAt = new LinearLayout.LayoutParams(-2, -2);
        labelAt.topMargin = dp(10);
        tile.addView(label, labelAt);
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(dp(190), dp(200));
        at.setMarginEnd(dp(18));
        tile.setLayoutParams(at);
        return tile;
    }
}
