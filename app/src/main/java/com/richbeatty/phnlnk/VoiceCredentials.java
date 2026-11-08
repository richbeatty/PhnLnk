package com.richbeatty.phnlnk;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class VoiceCredentials {
    private static final String STORE = "phnlnk_sip_gateway";
    private static final String ALIAS = "phnlnk_voice_bridge_secret_v1";

    static final class Data {
        final String server, user, password;
        Data(String server, String user, String password) {
            this.server = server;
            this.user = user;
            this.password = password;
        }
    }

    private VoiceCredentials() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    static boolean configured(Context c) {
        return prefs(c).getBoolean("configured", false);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean("enabled", false) && configured(c);
    }

    static void setEnabled(Context c, boolean value) {
        prefs(c).edit().putBoolean("enabled", value).apply();
    }

    static void save(Context c, String server, String user, String password) throws Exception {
        if (server == null || user == null || password == null)
            throw new IllegalArgumentException("All fields are required");
        String localIp = RemoteClient.normalizeHost(server);
        String safeUser = user.trim();
        if (!safeUser.matches("[a-zA-Z0-9_]{1,32}"))
            throw new IllegalArgumentException("SIP username must be letters or digits");
        if (password.length() < 12 || password.length() > 128)
            throw new IllegalArgumentException("Use a unique SIP password of at least 12 characters");

        byte[] iv;
        byte[] ciphertext;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey());
        iv = cipher.getIV();
        ciphertext = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
        boolean saved = prefs(c).edit()
                .putString("server", localIp)
                .putString("user", safeUser)
                .putString("password_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString("password_cipher", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putBoolean("configured", true)
                .putBoolean("enabled", false)
                .commit();
        if (!saved) throw new IllegalStateException("Could not save voice bridge credentials");
    }

    static Data load(Context c) throws Exception {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("configured", false))
            throw new IllegalStateException("Voice bridge is not configured");
        byte[] iv = Base64.decode(p.getString("password_iv", ""), Base64.DEFAULT);
        byte[] enc = Base64.decode(p.getString("password_cipher", ""), Base64.DEFAULT);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
        String password = new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
        return new Data(p.getString("server", ""), p.getString("user", ""), password);
    }

    static void forget(Context c) {
        prefs(c).edit().clear().apply();
    }

    private static SecretKey encryptionKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
