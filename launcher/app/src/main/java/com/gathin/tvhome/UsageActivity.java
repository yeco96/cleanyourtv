package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** How long the TV was used and in which apps: today, the last 7 days and the last 30 (usage_stats). Left/Right change the tab. */
public final class UsageActivity extends Activity {
    private static final int[] TAB_TITLES = {R.string.usage_today, R.string.usage_week, R.string.usage_month};
    private static final int[] TAB_DAYS = {1, 7, 30};
    private static final int ACCENT = 0xFFF5B945;

    private LinearLayout body;
    private LinearLayout tabs;
    private int tab;
    private float density;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101419);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(72), dp(38), dp(72), dp(38));
        scroll.addView(page);
        page.addView(text(getString(R.string.usage_title), 34, Color.WHITE));
        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabsAt = new LinearLayout.LayoutParams(-1, -2);
        tabsAt.topMargin = dp(20);
        page.addView(tabs, tabsAt);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bodyAt = new LinearLayout.LayoutParams(-1, -2);
        bodyAt.topMargin = dp(18);
        page.addView(body, bodyAt);
        setContentView(scroll);
        scroll.setFocusable(true);
        scroll.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && tab > 0) {
            tab--;
            render();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT && tab < TAB_TITLES.length - 1) {
            tab++;
            render();
            return true;
        }
        return super.onKeyDown(code, event);
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

    private void render() {
        tabs.removeAllViews();
        for (int i = 0; i < TAB_TITLES.length; i++) {
            TextView t = text(getString(TAB_TITLES[i]), 20, i == tab ? 0xFF101419 : 0xCCFFFFFF);
            t.setPadding(dp(22), dp(8), dp(22), dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(22));
            bg.setColor(i == tab ? ACCENT : 0x22FFFFFF);
            t.setBackground(bg);
            LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-2, -2);
            at.setMarginEnd(dp(10));
            tabs.addView(t, at);
        }
        body.removeAllViews();
        if (!UsageTracker.permitted(this)) {
            body.addView(text(getString(R.string.usage_no_permission, getPackageName()), 18, 0xCCFFFFFF));
            return;
        }
        UsageTracker.update(this, UsageGuard.currentProfile(this));
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.parse(UsageDays.dayOf(System.currentTimeMillis(), zone, UsageTracker.DAY_SHIFT_HOURS));
        int days = TAB_DAYS[tab];
        LocalDate from = today.minusDays(days - 1);
        List<UsageReport.Row> all = new UsageStore(this).since(from.minusDays(days).toString());   // one more period back, to compare
        List<UsageReport.Row> now = new ArrayList<>();
        for (UsageReport.Row r : all) if (r.day.compareTo(from.toString()) >= 0) now.add(r);
        long total = UsageReport.total(now, null);
        long before = UsageReport.total(all, null) - total;
        if (total == 0) {
            body.addView(text(getString(R.string.usage_nothing), 18, 0xCCFFFFFF));
            return;
        }
        body.addView(text(duration(total), 44, Color.WHITE));
        String sub = getString(R.string.usage_split, duration(UsageReport.total(now, UsageReport.ADULT)),
                duration(UsageReport.total(now, UsageReport.CHILD)));
        if (days > 1 && before > 0) {
            int change = UsageReport.changePercent(total, before);
            sub += "   ·   " + getString(R.string.usage_vs_before, (change > 0 ? "+" : "") + change + "%");
        }
        body.addView(text(sub, 16, 0xAAFFFFFF));
        if (days > 1) {
            long[] perDay = UsageReport.perDay(now, from, days, null);
            body.addView(text(getString(R.string.usage_average, duration(UsageReport.averageOfUsedDays(perDay))), 16, 0xAAFFFFFF));
            LinearLayout.LayoutParams barsAt = new LinearLayout.LayoutParams(-1, dp(120));
            barsAt.topMargin = dp(14);
            body.addView(dayBars(perDay, from), barsAt);
        }
        LinearLayout.LayoutParams headingAt = new LinearLayout.LayoutParams(-2, -2);
        headingAt.topMargin = dp(22);
        headingAt.bottomMargin = dp(6);
        body.addView(text(getString(R.string.usage_apps), 22, Color.WHITE), headingAt);
        List<UsageReport.AppTotal> apps = UsageReport.byApp(now, null);
        long top = apps.isEmpty() ? 1 : apps.get(0).seconds;
        for (int i = 0; i < Math.min(apps.size(), 10); i++) body.addView(appRow(apps.get(i), top));
    }

    /** One bar per day, as tall as that day's use against the busiest day; the label under it is the weekday (or the day). */
    private View dayBars(long[] perDay, LocalDate from) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        long max = Math.max(1, UsageReport.max(perDay));
        boolean month = perDay.length > 10;
        for (int i = 0; i < perDay.length; i++) {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            View bar = new View(this);
            GradientDrawable fill = new GradientDrawable();
            fill.setCornerRadius(dp(3));
            fill.setColor(perDay[i] == 0 ? 0x22FFFFFF : ACCENT);
            bar.setBackground(fill);
            int h = perDay[i] == 0 ? dp(3) : Math.max(dp(4), (int) (dp(92) * perDay[i] / max));
            column.addView(bar, new LinearLayout.LayoutParams(month ? dp(12) : dp(34), h));
            LocalDate day = from.plusDays(i);
            String label = month ? (day.getDayOfMonth() % 5 == 0 ? String.valueOf(day.getDayOfMonth()) : "")
                    : day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
            TextView name = text(label, 12, 0x99FFFFFF);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            column.addView(name);
            row.addView(column, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        return row;
    }

    private View appRow(UsageReport.AppTotal app, long top) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));
        TextView name = text(label(app.pkg), 18, Color.WHITE);
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(dp(260), -2));
        row.addView(new ShareBar(this, (float) app.seconds / top), new LinearLayout.LayoutParams(0, dp(10), 1f));
        TextView time = text(duration(app.seconds), 18, 0xCCFFFFFF);
        time.setGravity(Gravity.END);
        row.addView(time, new LinearLayout.LayoutParams(dp(110), -2));
        return row;
    }

    /** A bar that fills {@code share} of its width. */
    private static final class ShareBar extends View {
        private final float share;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ShareBar(Context context, float share) {
            super(context);
            this.share = share;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float r = getHeight() / 2f;
            paint.setColor(0x22FFFFFF);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), r, r, paint);
            paint.setColor(ACCENT);
            canvas.drawRoundRect(0, 0, Math.max(getHeight(), getWidth() * share), getHeight(), r, r, paint);
        }
    }

    private String label(String pkg) {
        PackageManager pm = getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private String duration(long seconds) {
        long minutes = (seconds + 30) / 60;
        if (minutes < 60) return getString(R.string.usage_minutes, minutes);
        return getString(R.string.usage_hours_minutes, minutes / 60, minutes % 60);
    }
}
