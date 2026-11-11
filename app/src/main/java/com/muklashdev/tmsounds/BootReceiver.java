package com.muklashdev.tmsounds;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Restarts the enhancer after a reboot if the user left it switched on. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences p =
                context.getSharedPreferences(FxEngine.PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean("enabled", false)) {
            AudioService.start(context);
        }
    }
}
