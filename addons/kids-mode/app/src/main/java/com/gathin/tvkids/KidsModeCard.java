package com.gathin.tvkids;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** The Kids Mode card: opens kids mode in the launcher (it turns it on, or asks for the PIN to manage it) and closes. */
public final class KidsModeCard extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            startActivity(new Intent().setComponent(new ComponentName("com.gathin.tvhome", "com.gathin.tvhome.KidModeLaunchActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.needs_launcher, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
