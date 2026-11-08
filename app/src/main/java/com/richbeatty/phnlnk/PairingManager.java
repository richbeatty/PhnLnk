package com.richbeatty.phnlnk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class PairingManager {
    private static PairingManager instance;
    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();
    private String code;
    private long expires;
    private int failures;

    private PairingManager(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences("phnlnk_v2_devices", Context.MODE_PRIVATE);
    }

    static synchronized PairingManager get(Context ctx) {
        if (instance == null) instance = new PairingManager(ctx);
        return instance;
    }

    synchronized String begin() {
        code = String.format(Locale.US, "%06d", random.nextInt(1000000));
        expires = SystemClock.elapsedRealtime() + 300000L;
        failures = 0;
        return code;
    }

    synchronized String current() {
        if (code == null || expires < SystemClock.elapsedRealtime()) {
            cancel();
            return null;
        }
        return code;
    }

    synchronized void cancel() {
        code = null;
        expires = 0;
        failures = 0;
    }

    synchronized int count() {
        return prefs.getStringSet("keys", new HashSet<>()).size();
    }

    synchronized String pair(String supplied) {
        String actual = current();
        if (actual == null) throw new IllegalArgumentException("Open PhnLnk on the host phone and tap Pair a device.");
        if (failures >= 5) {
            cancel();
            throw new IllegalArgumentException("Too many attempts. Start pairing again.");
        }
        failures++;
        if (supplied == null || !supplied.matches("[0-9]{6}") ||
                !MessageDigest.isEqual(supplied.getBytes(StandardCharsets.US_ASCII),
                        actual.getBytes(StandardCharsets.US_ASCII))) {
            if (failures >= 5) cancel();
            throw new IllegalArgumentException("Wrong code. Check the MP01.");
        }
        Set<String> keys = new HashSet<>(prefs.getStringSet("keys", new HashSet<>()));
        if (keys.size() >= 10) {
            cancel();
            throw new IllegalArgumentException("Too many devices. Clear paired devices on the host phone.");
        }
        byte[] value = new byte[32];
        random.nextBytes(value);
        String token = Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        keys.add(hash(token));
        if (!prefs.edit().putStringSet("keys", keys).commit()) {
            throw new IllegalStateException("Could not save this browser on the host phone");
        }
        cancel();
        return token;
    }

    synchronized boolean authorized(String header) {
        if (header == null || !header.startsWith("Bearer ")) return false;
        String token = header.substring(7);
        if (!token.matches("[A-Za-z0-9_-]{43}")) return false;
        byte[] digest = hash(token).getBytes(StandardCharsets.US_ASCII);
        for (String known : prefs.getStringSet("keys", new HashSet<>())) {
            if (MessageDigest.isEqual(digest, known.getBytes(StandardCharsets.US_ASCII))) return true;
        }
        return false;
    }

    /**
     * Proof-of-possession authentication for the encrypted LAN voice socket.
     * No bearer token is sent over the network. Host stores only SHA-256 of
     * pairing tokens; a companion derives that same secret locally.
     */
    synchronized byte[] findVoiceKey(byte[] serverNonce, byte[] clientNonce,
                                      byte[] proof) throws Exception {
        byte[] matched = null;
        for (String known : prefs.getStringSet("keys", new HashSet<>())) {
            if (!known.matches("[0-9a-f]{64}")) continue;
            byte[] key = VoiceSecureTransport.fromHex(known);
            byte[] expected = VoiceSecureTransport.proof(key,"client",serverNonce,clientNonce);
            if (MessageDigest.isEqual(expected,proof)) matched = key;
        }
        return matched;
    }

    synchronized void forgetAll() {
        VoiceRelayServer.disconnectActive();
        if (!prefs.edit().remove("keys").commit())
            throw new IllegalStateException("Could not clear devices");
        cancel();
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
            char[] hex = "0123456789abcdef".toCharArray();
            char[] result = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                result[2 * i] = hex[(digest[i] & 255) >> 4];
                result[2 * i + 1] = hex[digest[i] & 15];
            }
            return new String(result);
        } catch (Exception e) {
            throw new IllegalStateException("Hashing unavailable", e);
        }
    }
}