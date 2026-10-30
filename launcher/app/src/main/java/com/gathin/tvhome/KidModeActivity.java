package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Kids mode, one screen for its three uses (usage_limit): turning it on (sets the PIN the first time), managing it with
 * the PIN (extra time, the daily limit, a new PIN, turning it off), and the lock shown when the day's time is up.
 *
 * <p>The PIN is entered the way Android TV's own PIN asks for it: Up and Down change the number in the box, OK keeps it
 * and moves to the next box (the ones already kept show as dots), Left or Back takes the last one back. Number keys
 * work too. The master PIN ({@code kid_master_pin}, config.xml) at the PIN prompt lets a grown-up who forgot the PIN
 * choose a new one.
 */
public final class KidModeActivity extends Activity {
    static final String EXTRA_MODE = "mode";
    static final int MODE_ACTIVATE = 0, MODE_MANAGE = 1;
    private static final int ACCENT = 0xFFF5B945;

    private enum Stage { SET_PIN, REPEAT_PIN, ASK_PIN, OPTIONS }

    private int mode;
    private Stage stage;
    private String firstPin;
    /** A new PIN is being chosen for a mode that is already on (changing it, or after the master PIN). */
    private boolean replacing;
    private final int[] digits = new int[UsageLimit.PIN_DIGITS];
    private int cursor;
    private float density;
    private TextView title;
    private TextView hint;
    private LinearLayout pinRow;
    private LinearLayout options;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_MANAGE);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setBackgroundColor(0xFF101419);
        page.setPadding(dp(72), dp(60), dp(72), dp(38));
        title = text("", 34, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        page.addView(title);
        hint = text("", 18, 0xAAFFFFFF);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintAt = new LinearLayout.LayoutParams(-1, -2);
        hintAt.topMargin = dp(10);
        page.addView(hint, hintAt);
        pinRow = new LinearLayout(this);
        pinRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pinAt = new LinearLayout.LayoutParams(-2, -2);
        pinAt.topMargin = dp(30);
        page.addView(pinRow, pinAt);
        options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams optionsAt = new LinearLayout.LayoutParams(dp(520), -2);
        optionsAt.topMargin = dp(20);
        page.addView(options, optionsAt);
        setContentView(page);
        if (mode == MODE_ACTIVATE && !UsageGuard.hasPin(this)) {
            show(Stage.SET_PIN);
        } else if (mode == MODE_ACTIVATE) {
            UsageGuard.activate(this);   // a PIN is already set: turning it on needs none, turning it off does
            afterActivate();
        } else {
            show(Stage.ASK_PIN);
        }
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

    private void show(Stage next) {
        stage = next;
        options.removeAllViews();
        pinRow.removeAllViews();
        hint.setText(R.string.kid_pin_hint);
        if (next == Stage.OPTIONS) {
            showOptions();
            return;
        }
        title.setText(next == Stage.SET_PIN ? R.string.kid_set_pin : next == Stage.REPEAT_PIN ? R.string.kid_repeat_pin
                : R.string.kid_enter_pin);
        for (int i = 0; i < digits.length; i++) {
            digits[i] = 0;
            TextView box = text("0", 44, Color.WHITE);
            box.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(dp(78), dp(96));
            at.setMarginEnd(dp(14));
            pinRow.addView(box, at);
        }
        cursor = 0;
        paintDigits();
    }

    /** The box being set shows its number; the ones already kept show a dot; the ones to come are empty. */
    private void paintDigits() {
        for (int i = 0; i < pinRow.getChildCount(); i++) {
            TextView t = (TextView) pinRow.getChildAt(i);
            t.setText(i < cursor ? "•" : i == cursor ? String.valueOf(digits[i]) : "");
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(14));
            bg.setColor(i == cursor ? ACCENT : 0x22FFFFFF);
            t.setBackground(bg);
            t.setTextColor(i == cursor ? 0xFF101419 : Color.WHITE);
        }
    }

    private String pin() {
        StringBuilder s = new StringBuilder();
        for (int d : digits) s.append(d);
        return s.toString();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent event) {
        if (stage == Stage.OPTIONS) return super.onKeyDown(code, event);
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            digits[cursor] = code - KeyEvent.KEYCODE_0;
            keep();
        } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
            digits[cursor] = (digits[cursor] + 1) % 10;
        } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            digits[cursor] = (digits[cursor] + 9) % 10;
        } else if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            keep();
        } else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DEL
                || (code == KeyEvent.KEYCODE_BACK && cursor > 0)) {
            if (cursor > 0) cursor--;
        } else {
            return super.onKeyDown(code, event);
        }
        if (stage != Stage.OPTIONS && pinRow.getChildCount() > 0) paintDigits();
        return true;
    }

    /** Keeps the number in the box and moves on; the last box sends the PIN. */
    private void keep() {
        if (cursor < digits.length - 1) {
            cursor++;
        } else {
            submit();
        }
    }

    private void submit() {
        String entered = pin();
        switch (stage) {
            case SET_PIN:
                firstPin = entered;
                show(Stage.REPEAT_PIN);
                break;
            case REPEAT_PIN:
                if (entered.equals(firstPin)) {
                    UsageGuard.setPin(this, entered);
                    if (replacing) {
                        replacing = false;
                        Toast.makeText(this, R.string.kid_pin_changed, Toast.LENGTH_LONG).show();
                        show(Stage.OPTIONS);
                    } else {
                        UsageGuard.activate(this);
                        afterActivate();
                    }
                } else {
                    show(Stage.SET_PIN);
                    hint.setText(R.string.kid_pins_differ);
                }
                break;
            case ASK_PIN:
                int result = UsageGuard.attempt(this, entered, getString(R.string.kid_master_pin));
                if (result == UsageGuard.PIN_MASTER) {
                    replacing = true;   // the master PIN forgets the old one: a grown-up chooses a new one
                    show(Stage.SET_PIN);
                } else if (result == UsageGuard.PIN_OK) {
                    show(Stage.OPTIONS);
                } else {
                    show(Stage.ASK_PIN);
                    long wait = UsageGuard.waitLeftMs(this);
                    hint.setText(wait > 0 ? getString(R.string.kid_wait, wait / 1000 + 1) : getString(R.string.kid_wrong_pin));
                }
                break;
            default:
                break;
        }
    }

    /** "2 h", "45 min" or "no limit". */
    private String limitText(int minutes) {
        if (minutes == 0) return getString(R.string.kid_no_limit);
        return minutes % 60 == 0 ? getString(R.string.kid_hours, minutes / 60) : getString(R.string.usage_minutes, (long) minutes);
    }

    /** Mode on: the first time there is no list of apps, ask for it right away. */
    private void afterActivate() {
        Toast.makeText(this, R.string.kid_on_toast, Toast.LENGTH_LONG).show();
        if (!UsageGuard.appsChosen(this)) startActivity(new Intent(this, KidAppsActivity.class));
        finish();
    }

    private void showOptions() {
        title.setText(R.string.kid_options);
        hint.setText(UsageGuard.limitToday(this) > 0
                ? getString(R.string.kid_left_today, (UsageGuard.remainingSeconds(this) + 59) / 60)
                : getString(R.string.kid_used, (UsageGuard.usedSeconds(this) + 30) / 60));
        int keep = Math.max(0, options.indexOfChild(options.getFocusedChild()));
        options.removeAllViews();
        addOption(getString(R.string.kid_exit), () -> {
            UsageGuard.leaveByPin(this);
            Toast.makeText(this, R.string.kid_off_toast, Toast.LENGTH_LONG).show();
            finish();
        });
        addOption(getString(R.string.kid_limit_weekdays, limitText(UsageGuard.weekdayLimit(this))),
                () -> UsageGuard.setWeekdayLimit(this, UsageLimit.nextLimit(UsageGuard.weekdayLimit(this))));
        addOption(getString(R.string.kid_limit_weekend, limitText(UsageGuard.weekendLimit(this))),
                () -> UsageGuard.setWeekendLimit(this, UsageLimit.nextLimit(UsageGuard.weekendLimit(this))));
        addOption(getString(R.string.kid_extra, 15), () -> UsageGuard.addExtraMinutes(this, 15));
        addOption(getString(R.string.kid_extra, 30), () -> UsageGuard.addExtraMinutes(this, 30));
        int plan = UsageGuard.schedule(this);
        addOption(plan == 0 ? getString(R.string.kid_schedule_off) : getString(R.string.kid_schedule_on,
                String.format("%02d:00", KidsSchedule.startHour(plan)), String.format("%02d:00", KidsSchedule.endHour(plan))),
                () -> UsageGuard.setSchedule(this, KidsSchedule.next(UsageGuard.schedule(this))));
        addOption(getString(R.string.kid_pick_apps), () -> startActivity(new Intent(this, KidAppsActivity.class)));
        addOption(getString(R.string.kid_change_pin), () -> {
            replacing = true;
            show(Stage.SET_PIN);
        });
        addOption(getString(R.string.kid_close), this::finish);
        options.getChildAt(Math.min(keep, options.getChildCount() - 1)).requestFocus();
    }

    private void addOption(String label, Runnable action) {
        TextView row = text(label, 20, Color.WHITE);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(22), 0, dp(22), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackgroundResource(R.drawable.settings_choice_bg);
        options.addView(row, new LinearLayout.LayoutParams(-1, dp(52)));
        row.setOnClickListener(v -> {
            action.run();
            if (!isFinishing() && stage == Stage.OPTIONS) showOptions();   // the numbers on the rows follow the choice
        });
    }
}
