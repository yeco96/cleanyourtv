package com.gathin.tvhome;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** The seconds each app was in front, per day and profile, kept on the TV (usage.db). Nothing leaves the TV. */
final class UsageStore extends SQLiteOpenHelper {
    UsageStore(Context context) {
        super(context.getApplicationContext(), "usage.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE daily (day TEXT NOT NULL, pkg TEXT NOT NULL, profile TEXT NOT NULL, "
                + "seconds INTEGER NOT NULL, PRIMARY KEY (day, pkg, profile))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int from, int to) { }

    /** Adds seconds to a day's row, making it when there is none. */
    synchronized void add(String day, String pkg, String profile, long seconds) {
        if (seconds <= 0) return;
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT OR IGNORE INTO daily (day, pkg, profile, seconds) VALUES (?, ?, ?, 0)",
                new Object[]{day, pkg, profile});
        db.execSQL("UPDATE daily SET seconds = seconds + ? WHERE day = ? AND pkg = ? AND profile = ?",
                new Object[]{seconds, day, pkg, profile});
    }

    /** Every row from {@code firstDay} (an ISO date) on. */
    synchronized List<UsageReport.Row> since(String firstDay) {
        List<UsageReport.Row> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT day, pkg, profile, seconds FROM daily WHERE day >= ?", new String[]{firstDay})) {
            while (c.moveToNext()) rows.add(new UsageReport.Row(c.getString(0), c.getString(1), c.getString(2), c.getLong(3)));
        }
        return rows;
    }

    /** Seconds on one day, of one profile. */
    synchronized long daySeconds(String day, String profile) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(seconds), 0) FROM daily WHERE day = ? AND profile = ?", new String[]{day, profile})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    /** Forgets what was counted for these apps. */
    synchronized void removeApps(java.util.Collection<String> packages) {
        SQLiteDatabase db = getWritableDatabase();
        for (String pkg : packages) db.delete("daily", "pkg = ?", new String[]{pkg});
    }

    /** Drops what is older than {@code firstDay}: two years are kept, a few MB at most. */
    synchronized void prune(String firstDay) {
        getWritableDatabase().delete("daily", "day < ?", new String[]{firstDay});
    }

    synchronized void clearAll() {
        getWritableDatabase().delete("daily", null, null);
    }
}
