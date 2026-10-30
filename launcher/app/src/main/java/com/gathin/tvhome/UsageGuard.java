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

    static final int PIN_WRONG = 0, PIN_OK = 1, PIN_MASTER = 2, PIN_WAIT = 3;

    /** Checks a PIN try. After five wrong ones in a row each try waits (30 s doubling to 5 min), the master PIN included. */
    static int attempt(Context c, String pin, String master) {
        SharedPreferences p = prefs(c);
        if (System.currentTimeMillis() < p.getLong("wait_until", 0)) return PIN_WAIT;
        int result = pin.equals(master) ? PIN_MASTER
                : UsageLimit.matches(pin, p.getString("salt", ""), p.getString("hash", null)) ? PIN_OK : PIN_WRONG;
        if (result != PIN_WRONG) {
            p.edit().putInt("wrong", 0).putLong("wait_until", 0).apply();
            return result;
        }
        int wrong = p.getInt("wrong", 0) + 1;
        p.edit().putInt("wrong", wrong).putLong("wait_until", System.currentTimeMillis() + KidsPolicy.waitAfter(wrong)).apply();
        return PIN_WRONG;
    }

    static long waitLeftMs(Context c) {
        return Math.max(0, prefs(c).getLong("wait_until", 0) - System.currentTimeMillis());
    }

    /** The TV apps the kids may open; empty until a grown-up chose (or the defaults were ticked). */
    static java.util.Set<String> allowed(Context c) {
        return new java.util.HashSet<>(prefs(c).getStringSet("allowed", new java.util.HashSet<>()));
    }

    static boolean appsChosen(Context c) {
        return prefs(c).contains("allowed");
    }

    static void setAllowed(Context c, java.util.Set<String> packages) {
        prefs(c).edit().putStringSet("allowed", new java.util.HashSet<>(packages)).apply();
    }

    /** True when kids mode is on and has apps to allow: then Home is the kids' screen and the guard sends other apps back. */
    static boolean guarding(Context c) {
        return isActive(c) && (!allowed(c).isEmpty() || timeUp(c));
    }

    /** Today's date as the day key (a day runs from 05:00 to 05:00). */
    static String today() {
        return UsageDays.dayOf(System.currentTimeMillis(), ZoneId.systemDefault(), UsageTracker.DAY_SHIFT_HOURS);
    }

    /** Seconds the kids have used the TV today (the day runs from 05:00 to 05:00). */
    static long usedSeconds(Context c) {
        return UsageStore.of(c).daySeconds(today(), UsageReport.CHILD);
    }

    /** The daily limit for weekdays and for the weekend, in minutes (0: none). Two hours until changed. */
    static int weekdayLimit(Context c) {
        return prefs(c).getInt("limit_wd", UsageLimit.DEFAULT_LIMIT_MIN);
    }

    static int weekendLimit(Context c) {
        return prefs(c).getInt("limit_we", UsageLimit.DEFAULT_LIMIT_MIN);
    }

    static void setWeekdayLimit(Context c, int minutes) {
        prefs(c).edit().putInt("limit_wd", minutes).apply();
    }

    static void setWeekendLimit(Context c, int minutes) {
        prefs(c).edit().putInt("limit_we", minutes).apply();
    }

    /** Today's limit in minutes (0: none), by the day the kids' day is on. */
    static int limitToday(Context c) {
        boolean weekend = UsageLimit.isWeekend(java.time.LocalDate.parse(today()).getDayOfWeek());
        return weekend ? weekendLimit(c) : weekdayLimit(c);
    }

    /** Minutes a grown-up added for today; they lapse with the day. */
    static int extraMinutes(Context c) {
        SharedPreferences p = prefs(c);
        return today().equals(p.getString("extra_day", "")) ? p.getInt("extra", 0) : 0;
    }

    static void addExtraMinutes(Context c, int minutes) {
        prefs(c).edit().putString("extra_day", today()).putInt("extra", extraMinutes(c) + minutes).apply();
        prefs(c).edit().putInt("notice", 0).apply();   // the notices before the end come again
    }

    /** Takes back the extra time given today. */
    static void clearExtraMinutes(Context c) {
        prefs(c).edit().putInt("extra", 0).putInt("notice", 0).apply();
    }

    /** Seconds the kids have left today; effectively unlimited when there is no limit. */
    static long remainingSeconds(Context c) {
        int limit = limitToday(c);
        return limit == 0 ? Long.MAX_VALUE : UsageLimit.remainingSeconds(usedSeconds(c), limit, extraMinutes(c));
    }

    /** True when kids mode is on and today's time is used up: the kids' screen is all that opens. */
    static boolean timeUp(Context c) {
        return isActive(c) && limitToday(c) > 0 && remainingSeconds(c) <= 0;
    }

    /** The hour notice (1 or 2) already given today; 0 when none. */
    static int noticeGiven(Context c) {
        SharedPreferences p = prefs(c);
        return today().equals(p.getString("notice_day", "")) ? p.getInt("notice", 0) : 0;
    }

    /** Says so (once each) when the kids reach 1 hour and 2 hours today. Called by the watcher service and by Home. */
    static void checkNotice(Context c) {
        if (!isActive(c)) return;
        int limit = limitToday(c);
        int code;
        String text;
        if (limit > 0) {   // with a limit the notices count down to it: 10 minutes left or less, then 1
            long left = remainingSeconds(c);
            code = left <= 60 ? 2 : left <= 600 ? 1 : 0;
            text = code == 2 ? c.getString(R.string.kid_left_one) : c.getString(R.string.kid_left_ten, (left + 59) / 60);
        } else {   // without one they mark the hours used
            code = UsageLimit.hoursReached(usedSeconds(c));
            text = c.getString(code == 1 ? R.string.kid_hour_1 : R.string.kid_hour_2);
        }
        if (code > noticeGiven(c)) {
            setNoticeGiven(c, code);
            if (!UsageGuardService.card(text)) {
                android.widget.Toast.makeText(c.getApplicationContext(), text, android.widget.Toast.LENGTH_LONG).show();
            }
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
        prefs(c).edit().putBoolean("enabled", false).putBoolean("auto", false).apply();
    }

    private static int minuteOfDay() {
        java.time.LocalTime now = java.time.LocalTime.now();
        return now.getHour() * 60 + now.getMinute();
    }

    /** The automatic window chosen (0 is off). */
    static int schedule(Context c) {
        return prefs(c).getInt("schedule", 0);
    }

    static void setSchedule(Context c, int choice) {
        prefs(c).edit().putInt("schedule", choice).putString("suppressed", "").apply();
    }

    /** A grown-up left with the PIN: inside a window the schedule does not turn the mode on again that day. */
    static void leaveByPin(Context c) {
        SharedPreferences.Editor edit = prefs(c).edit().putBoolean("enabled", false).putBoolean("auto", false);
        if (KidsSchedule.inWindow(schedule(c), minuteOfDay())) edit.putString("suppressed", today() + ":" + schedule(c));
        edit.apply();
    }

    /**
     * Turns the mode on inside the chosen window and off again when it ends, but only a mode the schedule itself turned
     * on, and only once a PIN and some apps are set. Called from Home, its minute tick and the watcher service.
     */
    static void applySchedule(Context c) {
        SharedPreferences p = prefs(c);
        int choice = schedule(c);
        if (choice == 0) {
            if (p.getBoolean("auto", false)) p.edit().putBoolean("auto", false).apply();
            return;
        }
        if (KidsSchedule.inWindow(choice, minuteOfDay())) {
            if (!isActive(c) && hasPin(c) && !allowed(c).isEmpty() && !(today() + ":" + choice).equals(p.getString("suppressed", ""))) {
                p.edit().putBoolean("enabled", true).putBoolean("auto", true).apply();
                enableService(c);
            }
        } else if (isActive(c) && p.getBoolean("auto", false)) {
            p.edit().putBoolean("enabled", false).putBoolean("auto", false).apply();
        }
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
