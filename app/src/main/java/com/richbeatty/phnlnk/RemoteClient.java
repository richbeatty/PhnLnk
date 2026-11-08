package com.richbeatty.phnlnk;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class RemoteClient {
    private static final String STORE = "phnlnk_companion";
    private RemoteClient() { }

    static String address(Context c) {
        return c.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString("host", "");
    }

    static void changeAddress(Context c, String newAddress) {
        if (!isPaired(c)) throw new IllegalStateException("Pair this device first");
        String normalized = normalizeHost(newAddress);
        boolean saved = c.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
                .putString("host", "http://" + normalized + ":" + Config.PORT).commit();
        if (!saved) throw new IllegalStateException("Could not save the new address");
    }

    static boolean isPaired(Context c) {
        return !address(c).isEmpty() && !token(c).isEmpty();
    }

    private static String token(Context c) {
        return c.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString("token", "");
    }

    static String voiceToken(Context c) { return token(c); }

    static void forget(Context c) {
        c.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
                .remove("host").remove("token").apply();
    }

    static String normalizeHost(String raw) {
        if (raw == null) throw new IllegalArgumentException("Enter the host phone's Wi-Fi address");
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("http://")) s = s.substring(7);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(":47321")) s = s.substring(0, s.length() - 6);
        String[] octets = s.split("\\.", -1);
        if (octets.length != 4) throw new IllegalArgumentException("Enter a local IP address, such as 192.168.1.12");
        int[] parts = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!octets[i].matches("[0-9]{1,3}"))
                throw new IllegalArgumentException("Enter a numeric Wi-Fi IP address");
            parts[i] = Integer.parseInt(octets[i]);
            if (parts[i] > 255) throw new IllegalArgumentException("Invalid IP address");
        }
        boolean privateAddress = parts[0] == 10 ||
                (parts[0] == 172 && parts[1] >= 16 && parts[1] <= 31) ||
                (parts[0] == 192 && parts[1] == 168) ||
                // Encrypted private-network overlays (for example a user-managed
                // Tailscale/WireGuard tunnel), not public WAN or port forwarding.
                (parts[0] == 100 && parts[1] >= 64 && parts[1] <= 127);
        if (!privateAddress) throw new IllegalArgumentException("Only private Wi-Fi or authenticated VPN addresses are supported");
        return parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
    }

    static void pair(Context c, String ip, String code) throws Exception {
        String safeIp = normalizeHost(ip);
        if (code == null || !code.matches("[0-9]{6}"))
            throw new IllegalArgumentException("Enter the six-digit pairing code");
        JSONObject input = new JSONObject().put("code", code);
        JSONObject result = request("http://" + safeIp + ":" + Config.PORT,
                null, "POST", "/api/pair", input);
        String token = result.optString("token", "");
        if (!token.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalStateException("The host did not return a valid pairing key");
        if (!c.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
                .putString("host", "http://" + safeIp + ":" + Config.PORT)
                .putString("token", token).commit()) {
            throw new IllegalStateException("Could not remember this phone");
        }
    }

    static JSONObject status(Context c) throws Exception {
        return request(address(c), token(c), "GET", "/api/status", null);
    }

    static JSONObject call(Context c, String action, String number) throws Exception {
        if (!("dial".equals(action) || "answer".equals(action) || "end".equals(action) || "dtmf".equals(action))) {
            throw new IllegalArgumentException("Unsupported call action");
        }
        JSONObject body = new JSONObject();
        if ("dial".equals(action)) body.put("number", number == null ? "" : number);
        if ("dtmf".equals(action)) body.put("digit", number == null ? "" : number);
        return request(address(c), token(c), "POST", "/api/" + action, body);
    }

    private static JSONObject request(String host, String token, String method,
                                      String path, JSONObject body) throws Exception {
        if (host == null || host.isEmpty())
            throw new IllegalStateException("Connect to a phone first");
        HttpURLConnection connection = (HttpURLConnection)
                new URL(host + path).openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cache-Control", "no-store");
        if (token != null && !token.isEmpty())
            connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("X-PhnLnk", "1");
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream output = connection.getOutputStream()) {output.write(data);}
            }
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400)
                throw new IllegalStateException("Redirect refused");
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String response = "{}";
            if (input != null) {
                try (InputStream stream = input) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] chunk = new byte[2048];
                    int n;
                    while ((n = stream.read(chunk)) != -1 && bytes.size() <= 100000) {
                        bytes.write(chunk, 0, n);
                    }
                    response = bytes.toString("UTF-8");
                }
            }
            JSONObject json = new JSONObject(response);
            if (status == 401) {
                throw new IllegalStateException("Pairing was revoked. Connect again on your phone.");
            }
            if (status >= 400) {
                throw new IllegalStateException(json.optString("error", "Phone request failed"));
            }
            return json;
        } finally {
            connection.disconnect();
        }
    }
}
