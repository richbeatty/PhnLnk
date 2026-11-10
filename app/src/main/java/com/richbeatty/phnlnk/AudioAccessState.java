package com.richbeatty.phnlnk;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persisted choices and last-known state for the optional audio feasibility
 * helper. These states describe SHELL ACCESS, not completed cellular audio.
 */
final class AudioAccessState {
    static final String STORE = "phnlnk_audio_access";
    static final String NOT_STARTED = "not_started";
    static final String NEEDS_PAIRING = "needs_pairing";
    static final String CONNECTING = "connecting";
    static final String READY = "helper_ready";
    static final String DEBUGGING_OFF = "debugging_off";
    static final String ERROR = "error";
    static final String PAIRING = "pairing";

    private AudioAccessState() { }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    static boolean autoReconnect(Context context) {
        return prefs(context).getBoolean("auto_reconnect", false);
    }

    static boolean isPaired(Context context) {
        return context.getSharedPreferences("phnlnk_audio_bootstrap",
                Context.MODE_PRIVATE).getBoolean("adb_paired", false);
    }

    static String captureMethod(Context context) {
        String value=prefs(context).getString("capture_backend","AUTO");
        return "REDIRECT".equals(value)||"DOWNLINK".equals(value)||"CALL".equals(value)
                ?value:"AUTO";
    }

    static String uplinkMethod(Context context) {
        String value=prefs(context).getString("uplink_backend","AUTO");
        return "REDIRECT".equals(value)||"TELEPHONY".equals(value)
                ||"TELEPHONY_48K".equals(value)?value:"AUTO";
    }

    static String cycleCapture(Context context) {
        String[] methods={"AUTO","REDIRECT","DOWNLINK","CALL"};
        String current=captureMethod(context);
        int next=0;
        for(int i=0;i<methods.length;i++)if(methods[i].equals(current))next=(i+1)%methods.length;
        String selected=methods[next];
        prefs(context).edit().putString("capture_backend",selected).apply();
        return selected;
    }

    static String cycleUplink(Context context) {
        String[] methods={"AUTO","REDIRECT","TELEPHONY","TELEPHONY_48K"};
        String current=uplinkMethod(context);
        int next=0;
        for(int i=0;i<methods.length;i++)if(methods[i].equals(current))next=(i+1)%methods.length;
        String selected=methods[next];
        prefs(context).edit().putString("uplink_backend",selected).apply();
        return selected;
    }

    static int manualPort(Context context) {
        int port = prefs(context).getInt("manual_port", 0);
        return port >= 1024 && port <= 65535 ? port : 0;
    }

    static void setManualPort(Context context, int port) {
        if (port != 0 && (port < 1024 || port > 65535))
            throw new IllegalArgumentException("Port must be between 1024 and 65535");
        prefs(context).edit().putInt("manual_port", port).apply();
    }

    static void enable(Context context) {
        prefs(context).edit().putBoolean("auto_reconnect", true).apply();
    }

    static void disable(Context context) {
        prefs(context).edit().putBoolean("auto_reconnect", false)
                .putString("state", NOT_STARTED)
                .putString("message", "Automatic reconnect is off.")
                .apply();
    }

    static void set(Context context, String state, String message) {
        SharedPreferences.Editor edit = prefs(context).edit()
                .putString("state", state)
                .putString("message", message)
                .putLong("last_updated", System.currentTimeMillis());
        if (READY.equals(state)) edit.putLong("last_ready", System.currentTimeMillis());
        edit.apply();
    }

    static String state(Context context) {
        return prefs(context).getString("state",
                isPaired(context) ? CONNECTING : NEEDS_PAIRING);
    }

    static String message(Context context) {
        String result = prefs(context).getString("message", isPaired(context)
                ? "Android pairing saved. Checking the audio helper."
                : "One-time Android pairing has not been completed.");
        if (CONNECTING.equals(state(context)) &&
                System.currentTimeMillis() - prefs(context)
                        .getLong("last_updated",0) > 45000)
            return "The audio helper has not responded for 45 seconds. "
                    + "Keep Wireless Debugging on, then tap Retry. If discovery fails, "
                    + "enter the connection port displayed in Android's Wireless Debugging settings.";
        return result;
    }

    static String display(Context context) {
        String state = state(context);
        if (!autoReconnect(context)) return isPaired(context)
                ? "Saved pairing, automatic reconnect off"
                : "Not configured";
        if (READY.equals(state)) return "Helper ready. Call audio not verified.";
        if (CONNECTING.equals(state)) {
            long age = System.currentTimeMillis() -
                    prefs(context).getLong("last_updated", 0);
            return age > 45000 ? "Connection stalled. Tap Retry." : "Connecting to Android...";
        }
        if (PAIRING.equals(state)) return "Waiting for Android pairing approval...";
        if (DEBUGGING_OFF.equals(state)) return "Wireless Debugging needs attention";
        if (NEEDS_PAIRING.equals(state)) return "Pair Android once";
        return "Audio access setup needs attention";
    }
}
