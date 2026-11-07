package com.richbeatty.phnlnk;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.SecureRandom;

final class Config {
    static final int PORT = 47321;
    private static final String PREFS = "phnlnk_local";
    private static final String KEY = "pairing_secret";

    private Config() { }

    static synchronized String pairingKey(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String existing = preferences.getString(KEY, null);
        if (existing != null && existing.length() >= 32) {
            return existing;
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String secret = Base64.encodeToString(random, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        if (!preferences.edit().putString(KEY, secret).commit()) {
            throw new IllegalStateException("Could not persist the local pairing key");
        }
        return secret;
    }
}
