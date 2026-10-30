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
    private String lastFront;
    private static UsageGuardService running;
    private android.view.View card;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
    }

    /**
     * A big card on top of whatever is playing, for eight seconds, that takes no focus and no keys, so the video keeps
     * running and the remote keeps working. False when the service is not running (the caller falls back to a Toast).
     */
    static boolean card(String text) {
        UsageGuardService service = running;
        if (service == null) return false;
        service.handler.post(() -> service.showCard(text));
        return true;
    }

    private void showCard(String text) {
        android.view.WindowManager windows = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
        if (windows == null) return;
        if (card != null) {
            try {
                windows.removeView(card);
            } catch (IllegalArgumentException ignored) {
                // already gone
            }
        }
        float density = getResources().getDisplayMetrics().density;
        android.widget.TextView view = new android.widget.TextView(this);
        view.setText(text);
        view.setTextSize(30);
        view.setTextColor(0xFF101419);
        view.setGravity(android.view.Gravity.CENTER);
        view.setPadding((int) (36 * density), (int) (20 * density), (int) (36 * density), (int) (20 * density));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xF2F5B945);
        bg.setCornerRadius(28 * density);
        view.setBackground(bg);
        android.view.WindowManager.LayoutParams at = new android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT, android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        at.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        at.y = (int) (48 * density);
        try {
            windows.addView(view, at);
        } catch (RuntimeException e) {
            return;
        }
        card = view;
        handler.postDelayed(() -> {
            if (card != view) return;
            try {
                windows.removeView(view);
            } catch (IllegalArgumentException ignored) {
                // already gone
            }
            card = null;
        }, 8000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        check();
        CharSequence pkg = event.getPackageName();
        if (pkg != null) lastFront = pkg.toString();
        if (pkg == null || !UsageGuard.guarding(this)) return;
        // With the time used up nothing is allowed but the kids' screen.
        java.util.Set<String> allowed = UsageGuard.timeUp(this) ? java.util.Collections.<String>emptySet() : UsageGuard.allowed(this);
        if (!KidsPolicy.bounce(true, pkg.toString(), getPackageName(), allowed, keyboards())) return;
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
        boolean was = UsageGuard.guarding(this);
        UsageGuard.applySchedule(this);
        if (was && !UsageGuard.guarding(this)) {   // the window ended: the kids' screen gives way to the normal Home
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        if (!UsageGuard.isActive(this)) return;
        UsageTracker.update(this, UsageReport.CHILD);
        UsageGuard.checkNotice(this);
        if (UsageGuard.timeUp(this) && !getPackageName().equals(lastFront)) {   // time ran out inside an app: send it out
            startActivity(new Intent(this, KidsHomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        }
        if (!ticking) {
            ticking = true;
            handler.postDelayed(tick, UsageGuard.limitToday(this) > 0 ? 30_000L : TICK_MS);
        }
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        handler.removeCallbacks(tick);
        super.onDestroy();
    }
}
