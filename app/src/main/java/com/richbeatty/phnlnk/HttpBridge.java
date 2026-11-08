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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import org.json.JSONObject;

final class HttpBridge {
    private final Context context;
    private final PairingManager pairing;
    private final ExecutorService clients = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8));
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private ServerSocket listener;
    private Thread loop;

    HttpBridge(Context context) {
        this.context = context.getApplicationContext();
        this.pairing = PairingManager.get(context);
    }

    void start() throws Exception {
        listener = HostTlsIdentity.listener();
        listener.setReuseAddress(true);
        listener.bind(new InetSocketAddress(Config.PORT));
        loop = new Thread(() -> {
            while (!closed) {
                try {
                    Socket socket = listener.accept();
                    connections.add(socket);
                    try {
                        clients.execute(() -> {
                            try { serve(socket); }
                            finally { connections.remove(socket); }
                        });
                    } catch (RejectedExecutionException full) {
                        connections.remove(socket);
                        socket.close();
                    }
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
        if (loop != null) {
            loop.interrupt();
            try { loop.join(1000); }
            catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
        }
        for (Socket socket : connections) try { socket.close(); } catch (IOException ignored) { }
        connections.clear();
        clients.shutdownNow();
    }

    private void serve(Socket socket) {
        try (SocketDeadline deadline = SocketDeadline.after(socket, 15000);
             Socket connection = socket) {
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
            if ("GET".equals(method) && "/".equals(path)) {
                byte[] page;
                try (InputStream asset = context.getAssets().open("index.html")) {
                    page = readAll(asset);
                }
                respond(output, 200, "OK", "text/html; charset=utf-8", page, "");
                return;
            }
            if ("POST".equals(method) && "/api/pair".equals(path)) {
                if (!"1".equals(headers.get("x-phnlnk")) ||
                        !headers.getOrDefault("content-type", "").startsWith("application/json")) {
                    respond(output, 403, "Forbidden", "application/json",
                            "{\"error\":\"Invalid request\"}", "");
                    return;
                }
                String origin = headers.get("origin");
                String host = headers.get("host");
                if (origin != null && (host == null || !origin.equals("https://" + host))) {
                    respond(output, 403, "Forbidden", "application/json",
                            "{\"error\":\"Cross-origin request blocked\"}", "");
                    return;
                }
                int len;
                try { len = Integer.parseInt(headers.getOrDefault("content-length", "-1")); }
                catch (NumberFormatException error) { len = -1; }
                if (len < 0 || len > 128) {
                    respond(output, 413, "Payload Too Large", "application/json",
                            "{\"error\":\"Invalid pairing request\"}", "");
                    return;
                }
                byte[] inputBody = new byte[len];
                int offset = 0;
                while (offset < len) {
                    int n = input.read(inputBody, offset, len - offset);
                    if (n < 0) throw new IOException("Incomplete request");
                    offset += n;
                }
                try {
                    JSONObject body = new JSONObject(new String(inputBody, StandardCharsets.UTF_8));
                    String token = pairing.pair(body.optString("code", ""));
                    JSONObject response = new JSONObject().put("ok", true).put("token", token);
                    respond(output, 200, "OK", "application/json", response.toString(), "");
                } catch (Exception error) {
                    String message = error.getMessage() == null ? "Pairing failed" : error.getMessage();
                    JSONObject response = new JSONObject().put("error", message);
                    respond(output, 403, "Forbidden", "application/json", response.toString(), "");
                }
                return;
            }
            if (!pairing.authorized(headers.get("authorization"))) {
                respond(output, 401, "Unauthorized", "application/json",
                        "{\"error\":\"Connect with the MP01 pairing code\"}", "");
                return;
            }

            if ("GET".equals(method) && "/api/status".equals(path)) {
                respond(output, 200, "OK", "application/json; charset=utf-8",
                        CallController.status(context).toString(), "");
                return;
            }

            if ("POST".equals(method) &&
                    ("/api/dial".equals(path) || "/api/answer".equals(path) || "/api/end".equals(path) || "/api/dtmf".equals(path))) {
                if (!"1".equals(headers.get("x-phnlnk")) ||
                        !headers.getOrDefault("content-type", "").startsWith("application/json")) {
                    respond(output, 403, "Forbidden", "text/plain", "Invalid request origin", "");
                    return;
                }
                String origin = headers.get("origin");
                String host = headers.get("host");
                if (origin != null && (host == null || !origin.equals("https://" + host))) {
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
                        case "/api/dtmf": {
                            String digit = body.optString("digit", "");
                            if (!digit.matches("[0-9*#]")) throw new IllegalArgumentException("Enter one keypad digit");
                            PhoneInCallService.sendDtmf(digit.charAt(0));
                            break;
                        }
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
