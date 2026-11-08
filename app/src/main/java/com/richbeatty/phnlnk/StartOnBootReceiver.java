package com.richbeatty.phnlnk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/** Best-effort recovery after reboot or app update. Saved pairing tokens survive. */
public final class StartOnBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) &&
                !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        String role = context.getSharedPreferences("mode", Context.MODE_PRIVATE)
                .getString("role", "");
        try {
            if ("host".equals(role) &&
                    context.getSharedPreferences("mode", Context.MODE_PRIVATE)
                            .getBoolean("hosting_enabled", true)) {
                Intent start = new Intent(context, GatewayService.class);
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(start);
                else context.startService(start);
            }
            if ("client".equals(role) &&
                    RemoteClient.isPaired(context) &&
                    context.getSharedPreferences("companion_ui", Context.MODE_PRIVATE)
                            .getBoolean("alerts", false)) {
                Intent start = new Intent(context, CompanionService.class);
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(start);
                else context.startService(start);
            }
        } catch (RuntimeException error) {
            // Android may block FGS startup, especially after force-stop.
            // Saved pairing is unaffected; reopening the app restarts the service.
            Log.w("PhnLnk", "Automatic restart was blocked by Android", error);
        }
    }
}
