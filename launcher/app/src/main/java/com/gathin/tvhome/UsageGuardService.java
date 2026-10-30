package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

/**
 * While kids mode is on: counts the time the kids use and says so when it reaches one hour and two hours (usage_limit).
 * It blocks nothing. It only sees which window came to the front. Off, it does nothing and schedules nothing.
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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) check();
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
