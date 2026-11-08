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
        return prefs(context).getString("message", isPaired(context)
                ? "Android pairing saved. Checking the audio helper."
                : "One-time Android pairing has not been completed.");
    }

    static String display(Context context) {
        String state = state(context);
        if (!autoReconnect(context)) return isPaired(context)
                ? "Saved pairing, automatic reconnect off"
                : "Not configured";
        if (READY.equals(state)) return "Helper ready. Call audio not verified.";
        if (CONNECTING.equals(state)) return "Connecting to Android...";
        if (PAIRING.equals(state)) return "Waiting for Android pairing approval...";
        if (DEBUGGING_OFF.equals(state)) return "Wireless Debugging needs attention";
        if (NEEDS_PAIRING.equals(state)) return "Pair Android once";
        return "Audio access setup needs attention";
    }
}
