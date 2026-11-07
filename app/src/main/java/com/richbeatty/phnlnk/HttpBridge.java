package com.richbeatty.phnlnk;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

final class HttpBridge {
    private final Context context;
    private final String secret;
    private final ExecutorService clients = Executors.newFixedThreadPool(4);
    private volatile boolean closed;
    private ServerSocket listener;
    private Thread loop;

    HttpBridge(Context context, String secret) {
        this.context = context.getApplicationContext();
        this.secret = secret;
    }

    void start() throws IOException {
        listener = new ServerSocket();
        listener.setReuseAddress(true);
        listener.bind(new InetSocketAddress(Config.PORT));
        loop = new Thread(() -> {
            while (!closed) {
                try {
                    Socket socket = listener.accept();
                    clients.execute(() -> serve(socket));
                } catch (Exception exception) {
                    if (!closed) android.util.Log.w("PhnLnk", "Connection failed", exception);
                }
            }
        }, "PhnLnk-accept");
        loop.start();
    }

    void close() {
        closed = true;
        try {
            if (listener != null) listener.close();
        } catch (IOException ignored) { }
        clients.shutdownNow();
        if (loop != null) loop.interrupt();
    }

    private void serve(Socket socket) {
        try (Socket connection = socket) {
            connection.setSoTimeout(10000);
            InputStream input = connection.getInputStream();
            OutputStream output = connection.getOutputStream();
            String request = readLine(input, 2048);
            if (request == null) return;
            String[] line = request.split(" ");
            if (line.length != 3 || !line[2].startsWith("HTTP/1.")) {
                respond(output, 400, "Bad Request", "text/plain", "Invalid HTTP request", "");
                return;
            }
            String method = line[0];
            String path = line[1].split("\\?", 2)[0];
            Map<String, String> headers = new HashMap<>();
            boolean completed = false;
            for (int n = 0; n < 40; n++) {
                String header = readLine(input, 4096);
                if (header == null) break;
                if (header.isEmpty()) {
                    completed = true;
                    break;
                }
                int colon = header.indexOf(':');
                if (colon > 0) {
                    headers.put(header.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                            header.substring(colon + 1).trim());
                }
            }
            if (!completed) {
                respond(output, 431, "Request Header Fields Too Large",
                        "text/plain", "Headers too large", "");
                return;
            }
            if (!authenticated(headers.get("authorization"))) {
                respond(output, 401, "Unauthorized", "text/plain",
                        "Pair with the key shown on the MP01",
                        "WWW-Authenticate: Basic realm=\"PhnLnk\", charset=\"UTF-8\"\r\n");
                return;
            }

            if ("GET".equals(method) && "/".equals(path)) {
                byte[] page;
                try (InputStream asset = context.getAssets().open("index.html")) {
                    page = readAll(asset);
                }
                respond(output, 200, "OK", "text/html; charset=utf-8", page, "");
                return;
            }
            if ("GET".equals(method) && "/api/status".equals(path)) {
                respond(output, 200, "OK", "application/json; charset=utf-8",
                        CallController.status(context).toString(), "");
                return;
            }

            if ("POST".equals(method) &&
                    ("/api/dial".equals(path) || "/api/answer".equals(path) || "/api/end".equals(path))) {
                if (!"1".equals(headers.get("x-phnlnk")) ||
                        !headers.getOrDefault("content-type", "").startsWith("application/json")) {
                    respond(output, 403, "Forbidden", "text/plain", "Invalid request origin", "");
                    return;
                }
                String origin = headers.get("origin");
                String host = headers.get("host");
                if (origin != null && (host == null || !origin.equals("http://" + host))) {
                    respond(output, 403, "Forbidden", "text/plain", "Cross-origin requests blocked", "");
                    return;
                }
                int length;
                try {
                    length = Integer.parseInt(headers.getOrDefault("content-length", "-1"));
                } catch (NumberFormatException exception) {
                    length = -1;
                }
                if (length < 0 || length > 1024) {
                    respond(output, 413, "Payload Too Large", "text/plain", "Invalid body length", "");
                    return;
                }
                byte[] payload = new byte[length];
                int offset = 0;
                while (offset < length) {
                    int n = input.read(payload, offset, length - offset);
                    if (n < 0) throw new IOException("Incomplete request body");
                    offset += n;
                }
                JSONObject body = new JSONObject(new String(payload, StandardCharsets.UTF_8));
                try {
                    switch (path) {
                        case "/api/dial": CallController.dial(context, body.optString("number", "")); break;
                        case "/api/answer": CallController.answer(context); break;
                        case "/api/end": CallController.end(context); break;
                        default: throw new IllegalStateException("Unknown action");
                    }
                    respond(output, 200, "OK", "application/json", "{\"ok\":true}", "");
                } catch (Exception exception) {
                    JSONObject error = new JSONObject().put("error",
                            exception.getMessage() == null ? "Action failed" : exception.getMessage());
                    respond(output, 409, "Conflict", "application/json", error.toString(), "");
                }
                return;
            }
            respond(output, 404, "Not Found", "text/plain", "Not found", "");
        } catch (Exception exception) {
            android.util.Log.w("PhnLnk", "Request failed", exception);
        }
    }

    private boolean authenticated(String supplied) {
        if (supplied == null || !supplied.startsWith("Basic ")) return false;
        try {
            String decoded = new String(
                    java.util.Base64.getDecoder().decode(supplied.substring(6).trim()),
                    StandardCharsets.UTF_8);
            byte[] actual = decoded.getBytes(StandardCharsets.UTF_8);
            byte[] expected = ("phnlnk:" + secret).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String readLine(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (bytes.size() < limit) {
            int value = input.read();
            if (value == -1) return null;
            if (value == '\n') {
                byte[] data = bytes.toByteArray();
                int length = data.length > 0 && data[data.length - 1] == '\r'
                        ? data.length - 1 : data.length;
                return new String(data, 0, length, StandardCharsets.US_ASCII);
            }
            bytes.write(value);
        }
        throw new IOException("HTTP line too long");
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = input.read(buffer)) != -1) result.write(buffer, 0, n);
        return result.toByteArray();
    }

    private static void respond(OutputStream output, int status, String reason, String type,
                                String text, String extraHeaders) throws IOException {
        respond(output, status, reason, type, text.getBytes(StandardCharsets.UTF_8), extraHeaders);
    }

    private static void respond(OutputStream output, int status, String reason, String type,
                                byte[] data, String extraHeaders) throws IOException {
        String header = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + data.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Referrer-Policy: no-referrer\r\n"
                + "Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; "
                + "style-src 'self' 'unsafe-inline'; connect-src 'self'; form-action 'self'\r\n"
                + "Connection: close\r\n" + extraHeaders + "\r\n";
        output.write(header.getBytes(StandardCharsets.US_ASCII));
        output.write(data);
        output.flush();
    }
}
