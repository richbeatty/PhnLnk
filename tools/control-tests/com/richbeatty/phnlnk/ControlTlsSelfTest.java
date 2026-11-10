package com.richbeatty.phnlnk;

import java.io.InputStream;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/** Uses production TLS code and real self-signed certificates, never mock encryption. */
public final class ControlTlsSelfTest {
    private static int passed;
    private static final char[] PASSWORD = "test-identity-only".toCharArray();
    private interface ServerCase { void run(SSLSocket socket) throws Exception; }
    private interface ClientCase { void run(int port) throws Exception; }

    private static KeyStore identity(String file) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream stream = Files.newInputStream(Path.of(file))) { store.load(stream, PASSWORD); }
        return store;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void exchange(String name, KeyStore identity, ServerCase server, ClientCase client)
            throws Exception {
        try (SSLServerSocket listener = (SSLServerSocket)ControlTls.serverContext(identity, PASSWORD, "host")
                .getServerSocketFactory().createServerSocket(0, 4, java.net.InetAddress.getLoopbackAddress())) {
            listener.setSoTimeout(4000);
            ExecutorService worker = Executors.newSingleThreadExecutor();
            Future<?> remote = worker.submit(() -> {
                try (SSLSocket socket = (SSLSocket)listener.accept()) {
                    socket.setSoTimeout(4000);
                    server.run(socket);
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            try {
                client.run(listener.getLocalPort());
                remote.get(6, TimeUnit.SECONDS);
                System.out.println("PASS " + name);
                passed++;
            } finally { worker.shutdownNow(); }
        }
    }

    private static SSLSocket connect(SSLContext tls, int port) throws Exception {
        SSLSocket socket = (SSLSocket)tls.getSocketFactory().createSocket("127.0.0.1", port);
        socket.setSoTimeout(4000);
        return socket;
    }

    public static void main(String[] args) throws Exception {
        KeyStore first = identity(args[0]), second = identity(args[1]);
        String pin = ControlTls.fingerprint((X509Certificate)first.getCertificate("host"));
        check(pin.matches("[0-9a-f]{64}"), "Invalid fingerprint");
        check(ControlTls.displayPin(pin).replace(" ", "").equals(pin), "Display lost fingerprint bits");
        for (String bad : new String[]{"", "a".repeat(63), "z".repeat(64), pin.toUpperCase()}) {
            try { ControlTls.pinnedContext(bad); throw new AssertionError("Invalid pin accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("PASS strict SHA-256 pin validation"); passed++;

        exchange("pinned TLS exchanges authenticated application bytes", first, socket -> {
            socket.startHandshake();
            check(socket.getInputStream().read() == 42, "Request corrupted");
            socket.getOutputStream().write(73); socket.getOutputStream().flush();
        }, port -> {
            try (SSLSocket socket = connect(ControlTls.pinnedContext(pin), port)) {
                socket.startHandshake();
                String requiredProtocol = System.getProperty("jdk.tls.client.protocols");
                if (requiredProtocol != null)
                    check(requiredProtocol.equals(socket.getSession().getProtocol()),
                            "Requested TLS version was not negotiated");
                check(ControlTls.matches(pin, socket.getSession()), "Hostname pin verifier rejected identity");
                check(!ControlTls.matches("0".repeat(64), socket.getSession()), "Wrong session pin accepted");
                socket.getOutputStream().write(42); socket.getOutputStream().flush();
                check(socket.getInputStream().read() == 73, "Reply corrupted");
            }
        });
        exchange("changed host certificate fails closed before request", second, socket -> {
            try {
                socket.startHandshake();
                check(socket.getInputStream().read() == -1, "Application data reached an impostor");
            } catch (javax.net.ssl.SSLException | SocketException expected) {
                // TLS 1.3 may still be writing the server Finished when the
                // rejecting client closes. A TCP broken pipe/reset is valid
                // here; the client must independently reject the certificate.
            }
        }, port -> {
            try (SSLSocket socket = connect(ControlTls.pinnedContext(pin), port)) {
                try { socket.startHandshake(); throw new AssertionError("Wrong certificate accepted"); }
                catch (SSLHandshakeException expected) { }
            }
        });
        exchange("discovery reads candidate without sending pairing or token", first, socket -> {
            socket.startHandshake();
            check(socket.getInputStream().read() == -1, "Discovery sent application data");
        }, port -> check(pin.equals(ControlTls.discoverUntrustedFingerprint("127.0.0.1", port)),
                "Discovery returned wrong candidate"));
        exchange("ordinary trust still rejects self-signed identity", first, socket -> {
            try { socket.startHandshake(); }
            catch (javax.net.ssl.SSLException | SocketException expected) { }
        }, port -> {
            try (SSLSocket socket = connect(SSLContext.getDefault(), port)) {
                try { socket.startHandshake(); throw new AssertionError("Global trust was weakened"); }
                catch (SSLHandshakeException expected) { }
            }
        });
        check(passed == 5, "Not all control TLS tests ran");
        System.out.println("All " + passed + " control TLS tests passed.");
    }
}
