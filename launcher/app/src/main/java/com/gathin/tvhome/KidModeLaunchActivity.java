package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

/**
 * "Kids mode" as an app of the TV: it shows in the app list like any other, so it can be put in the dock. Opened, it
 * turns the mode on (asking for the PIN the first time), or, with the mode already on, asks for the PIN to manage it.
 */
public final class KidModeLaunchActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, KidModeActivity.class).putExtra(KidModeActivity.EXTRA_MODE,
                UsageGuard.isActive(this) ? KidModeActivity.MODE_MANAGE : KidModeActivity.MODE_ACTIVATE));
        finish();
    }

    /**
     * True for the launcher's own entries that are not this one: the app list leaves them out, as it always has (Home
     * itself is not a tile), but lets this entry through.
     */
    static boolean isOtherOwn(String self, ActivityInfo info) {
        return info != null && self.equals(info.packageName) && !KidModeLaunchActivity.class.getName().equals(info.name);
    }
}
