package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * What the Kids Mode module card opens: it turns kids mode on (asking for the PIN the first time),
 * or, with the mode already on, asks for the PIN to manage it. Exported so the add-on can start it, and not an entry of
 * the app list.
 */
public final class KidModeLaunchActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, KidModeActivity.class).putExtra(KidModeActivity.EXTRA_MODE,
                UsageGuard.isActive(this) ? KidModeActivity.MODE_MANAGE : KidModeActivity.MODE_ACTIVATE));
        finish();
    }
}
