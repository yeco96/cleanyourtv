package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The parents' list of TV apps the kids can open in kids mode; OK ticks or unticks one, and each change is kept at once. */
public final class KidAppsActivity extends Activity {
    private Set<String> chosen;
    private float density;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        PackageManager pm = getPackageManager();
        Map<String, String> apps = new LinkedHashMap<>();
        List<ResolveInfo> found = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (!pkg.equals(getPackageName()) && !apps.containsKey(pkg)) apps.put(pkg, String.valueOf(ri.loadLabel(pm)));
        }
        List<String> order = new ArrayList<>(apps.keySet());
        order.sort((a, b) -> apps.get(a).compareToIgnoreCase(apps.get(b)));
        if (UsageGuard.appsChosen(this)) {
            chosen = UsageGuard.allowed(this);
        } else {
            chosen = new HashSet<>();
            for (String pkg : order) if (KidsPolicy.kidsApp(pkg, apps.get(pkg))) chosen.add(pkg);
            UsageGuard.setAllowed(this, chosen);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101419);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(72), dp(38), dp(72), dp(38));
        scroll.addView(page);
        page.addView(text(getString(R.string.kid_pick_title), 34, Color.WHITE));
        TextView hint = text(getString(R.string.kid_pick_hint), 18, 0xAAFFFFFF);
        LinearLayout.LayoutParams hintAt = new LinearLayout.LayoutParams(-1, -2);
        hintAt.topMargin = dp(8);
        hintAt.bottomMargin = dp(16);
        page.addView(hint, hintAt);
        LinearLayout first = null;
        for (String pkg : order) {
            LinearLayout row = row(pm, pkg, apps.get(pkg));
            page.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
            if (first == null) first = row;
        }
        setContentView(scroll);
        if (first != null) first.requestFocus();
    }

    private int dp(int value) {
        return Math.round(value * density);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout row(PackageManager pm, String pkg, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(18), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackgroundResource(R.drawable.settings_choice_bg);
        ImageView icon = new ImageView(this);
        try {
            icon.setImageDrawable(pm.getApplicationIcon(pkg));
        } catch (PackageManager.NameNotFoundException e) {
            icon.setImageDrawable(pm.getDefaultActivityIcon());
        }
        LinearLayout.LayoutParams iconAt = new LinearLayout.LayoutParams(dp(36), dp(36));
        iconAt.setMarginEnd(dp(16));
        row.addView(icon, iconAt);
        TextView name = text(label, 20, Color.WHITE);
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView mark = text(chosen.contains(pkg) ? "✓" : "", 26, 0xFFF5B945);
        row.addView(mark, new LinearLayout.LayoutParams(dp(40), -2));
        row.setOnClickListener(v -> {
            if (!chosen.remove(pkg)) chosen.add(pkg);
            UsageGuard.setAllowed(this, chosen);
            mark.setText(chosen.contains(pkg) ? "✓" : "");
        });
        return row;
    }
}
