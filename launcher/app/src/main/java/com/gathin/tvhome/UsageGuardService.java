package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

/**
 * While kids mode is on: counts the time the kids use and says so when it reaches one hour and two hours (usage_limit).
 * With allowed apps chosen it also sends any other app back to the kids' screen (KidsHomeActivity). It only sees which window came to the front. Off, it does nothing and schedules nothing.
 */
public final class UsageGuardService extends AccessibilityService {
    private static final long TICK_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean ticking;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            ticking = false;
            check();
        }
    };

    private long lastBounce;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        check();
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !UsageGuard.guarding(this)) return;
        if (!KidsPolicy.bounce(true, pkg.toString(), getPackageName(), UsageGuard.allowed(this), keyboards())) return;
        // One bounce per half second: some apps change windows several times as they start.
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastBounce < 500) return;
        lastBounce = now;
        startActivity(new Intent(this, KidsHomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    private java.util.Set<String> keyboards() {
        java.util.Set<String> out = new java.util.HashSet<>();
        android.view.inputmethod.InputMethodManager imm = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (imm != null) for (android.view.inputmethod.InputMethodInfo i : imm.getEnabledInputMethodList()) out.add(i.getPackageName());
        return out;
    }

    @Override
    public void onInterrupt() { }

    private void check() {
        if (!UsageGuard.isActive(this)) return;
        UsageTracker.update(this, UsageReport.CHILD);
        UsageGuard.checkNotice(this);
        if (!ticking) {
            ticking = true;
            handler.postDelayed(tick, TICK_MS);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }
}
