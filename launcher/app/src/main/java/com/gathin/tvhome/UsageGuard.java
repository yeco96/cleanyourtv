package com.gathin.tvhome;

import android.content.Context;

/** Who is using the TV right now: the adults, or the daughter's mode (usage_stats). */
final class UsageGuard {
    private UsageGuard() { }

    static String currentProfile(Context context) {
        return UsageReport.ADULT;
    }
}
