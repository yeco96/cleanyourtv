package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.security.SecureRandom;
import java.time.ZoneId;

/** Kids mode: a daily limit on how long the TV is used while it is on, and the PIN that lifts it (usage_limit). */
final class UsageGuard {
    private static final String PREFS = "usage_kid";
    private static final String SERVICE = "com.gathin.tvhome/.UsageGuardService";

    private UsageGuard() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isActive(Context c) {
        return prefs(c).getBoolean("enabled", false);
    }

    /** Who the time being read belongs to: the kids while their mode is on, the grown-ups otherwise. */
    static String currentProfile(Context c) {
        return isActive(c) ? UsageReport.CHILD : UsageReport.ADULT;
    }

    static boolean hasPin(Context c) {
        return prefs(c).getString("hash", null) != null;
    }

    static void setPin(Context c, String pin) {
        byte[] raw = new byte[8];
        new SecureRandom().nextBytes(raw);
        StringBuilder salt = new StringBuilder();
        for (byte b : raw) salt.append(String.format("%02x", b));
        prefs(c).edit().putString("salt", salt.toString()).putString("hash", UsageLimit.hash(pin, salt.toString())).apply();
    }

    static boolean checkPin(Context c, String pin) {
        SharedPreferences p = prefs(c);
        return UsageLimit.matches(pin, p.getString("salt", ""), p.getString("hash", null));
    }

    private static String today() {
        return UsageDays.dayOf(System.currentTimeMillis(), ZoneId.systemDefault(), UsageTracker.DAY_SHIFT_HOURS);
    }

    /** Seconds the kids have used the TV today (the day runs from 05:00 to 05:00). */
    static long usedSeconds(Context c) {
        return new UsageStore(c).daySeconds(today(), UsageReport.CHILD);
    }

    /** The hour notice (1 or 2) already given today; 0 when none. */
    static int noticeGiven(Context c) {
        SharedPreferences p = prefs(c);
        return today().equals(p.getString("notice_day", "")) ? p.getInt("notice", 0) : 0;
    }

    /** Says so (once each) when the kids reach 1 hour and 2 hours today. Called by the watcher service and by Home. */
    static void checkNotice(Context c) {
        if (!isActive(c)) return;
        int hours = UsageLimit.hoursReached(usedSeconds(c));
        if (hours > noticeGiven(c)) {
            setNoticeGiven(c, hours);
            android.widget.Toast.makeText(c.getApplicationContext(), hours == 1 ? R.string.kid_hour_1 : R.string.kid_hour_2,
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    static void setNoticeGiven(Context c, int hours) {
        prefs(c).edit().putString("notice_day", today()).putInt("notice", hours).apply();
    }

    static void activate(Context c) {
        prefs(c).edit().putBoolean("enabled", true).apply();
        enableService(c);
    }

    static void deactivate(Context c) {
        prefs(c).edit().putBoolean("enabled", false).apply();
    }

    /**
     * The launcher holds WRITE_SECURE_SETTINGS (granted over adb), so it can switch its own watcher on. Called again each
     * time Home opens while the mode is on: installing the launcher rewrites the list of accessibility services.
     */
    static void ensureService(Context c) {
        if (isActive(c)) enableService(c);
    }

    private static void enableService(Context c) {
        try {
            String now = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (now != null && now.contains(new ComponentName(c, UsageGuardService.class).flattenToShortString())) return;
            String next = (now == null || now.isEmpty() || now.equals("null")) ? SERVICE : now + ":" + SERVICE;
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
            Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (SecurityException e) {
            // No WRITE_SECURE_SETTINGS: kids mode still counts time, but cannot close apps until the service is switched on.
        }
    }
}
