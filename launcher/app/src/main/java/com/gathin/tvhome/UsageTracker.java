package com.gathin.tvhome;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads what Android recorded since the last read and adds it to the day totals (usage_stats). Called when Home is on
 * screen and by the guard service: nothing runs in between, Android keeps the events for days.
 */
final class UsageTracker {
    /** A day runs from this hour to the same hour next day, so a late film is still "yesterday's". */
    static final int DAY_SHIFT_HOURS = 5;
    private static final long MAX_SPAN_MS = 6L * 60 * 60 * 1000;
    private static final long FIRST_READ_MS = 2L * 24 * 60 * 60 * 1000;
    private static final String PREFS = "usage_state";
    private static final Object LOCK = new Object();

    private UsageTracker() { }

    static boolean permitted(Context context) {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName())
                == AppOpsManager.MODE_ALLOWED;
    }

    /** The apps that never count: this launcher, the TV's own home, the system interface and the input pickers. */
    static Set<String> ignored(Context context) {
        Set<String> out = new HashSet<>();
        out.add(context.getPackageName());
        out.add("android");
        out.add("com.android.systemui");
        out.add("com.android.tv.settings");
        // The screensaver is not use: the one set in Settings, and Google's Ambient mode that most TVs ship with.
        out.add("com.google.android.apps.tv.dreamx");
        out.add("com.google.android.backdrop");
        String dream = android.provider.Settings.Secure.getString(context.getContentResolver(), "screensaver_components");
        if (dream != null) {
            for (String part : dream.split(",")) {
                android.content.ComponentName name = android.content.ComponentName.unflattenFromString(part);
                if (name != null) out.add(name.getPackageName());
            }
        }
        PackageManager pm = context.getPackageManager();
        for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
            out.add(r.activityInfo.packageName);
        }
        return out;
    }

    /** Adds what happened since the last call; returns the seconds just added to the profile in {@code profile}. */
    static long update(Context context, String profile) {
        if (!permitted(context)) return 0;
        synchronized (LOCK) {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return 0;
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            long from = prefs.getLong("last_ms", now - FIRST_READ_MS);
            if (from > now) from = now;   // the clock was set back
            List<UsageSessions.Event> events = new ArrayList<>();
            UsageEvents.Event raw = new UsageEvents.Event();
            UsageEvents feed = usm.queryEvents(from, now);
            while (feed != null && feed.hasNextEvent()) {
                feed.getNextEvent(raw);
                events.add(new UsageSessions.Event(raw.getTimeStamp(), raw.getEventType(), raw.getPackageName(), raw.getClassName()));
            }
            UsageSessions.State state = new UsageSessions.State();
            state.pkg = prefs.getString("open_pkg", null);
            state.cls = prefs.getString("open_cls", null);
            state.openedAt = prefs.getLong("open_at", 0);
            state.countedUntil = prefs.getLong("open_until", 0);
            UsageStore store = UsageStore.of(context);
            Set<String> skip = ignored(context);
            store.removeApps(skip);   // what an older read counted before the app was known not to be use
            List<UsageSessions.Span> spans = UsageSessions.fold(state, events, now, skip, MAX_SPAN_MS);
            ZoneId zone = ZoneId.systemDefault();
            long added = 0;
            for (UsageSessions.Span span : spans) {
                for (UsageDays.Part part : UsageDays.split(span.start, span.end, zone, DAY_SHIFT_HOURS)) {
                    store.add(part.day, span.pkg, profile, part.seconds);
                    added += part.seconds;
                }
            }
            store.prune(UsageDays.dayOf(now - 730L * 24 * 60 * 60 * 1000, zone, DAY_SHIFT_HOURS));
            prefs.edit().putLong("last_ms", now).putString("open_pkg", state.pkg).putString("open_cls", state.cls)
                    .putLong("open_at", state.openedAt).putLong("open_until", state.countedUntil).apply();
            return added;
        }
    }
}
