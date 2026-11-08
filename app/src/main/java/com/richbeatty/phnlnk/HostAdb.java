package com.richbeatty.phnlnk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.net.wifi.WifiManager;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import android.util.Log;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.android.AdbMdns;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Self-ADB bootstrap. This is intentionally distinct from a root or Shizuku
 * integration. Android Wireless Debugging must be explicitly enabled and
 * paired by the owner. Shell access never opens a public network endpoint.
 *
 * Run all methods off the main/UI thread.
 */
final class HostAdb {
    private static final String TAG = "PhnLnkAudio";
    private static final String KEY_FILE = "host_adb_identity.pk8";
    private static final String CERT_FILE = "host_adb_identity.der";
    private static final String LOOPBACK = "127.0.0.1";
    private static final String PROBE_ASSET = "phnlnk-probe.jar";
    private static final String PROBE_DEVICE_PATH = "/data/local/tmp/phnlnk-audio-probe.jar";
    private static final String PROBE_LOG = "/data/local/tmp/phnlnk-audio-probe.log";
    private static final String MAIN_CLASS = "com.richbeatty.phnlnk.shell.AudioProbe";
    private static final String PREFS = "phnlnk_audio_bootstrap";

    interface Progress { void onStep(String message); }

    private static void progress(Progress listener, String message) {
        if (listener != null) listener.onStep(message);
    }

    private final Context context;
    private final KeyIdentity identity;

    HostAdb(Context context) throws Exception {
        this.context = context.getApplicationContext();
        identity = KeyIdentity.loadOrCreate(this.context);
    }

    boolean hasIdentity() {
        return new File(context.getFilesDir(), CERT_FILE).isFile();
    }

    String pair(String code) throws Exception {
        if (code == null || !code.matches("[0-9]{6}"))
            throw new IllegalArgumentException("Enter all six digits from Android's pairing dialog.");
        int port = discover(AdbMdns.SERVICE_TYPE_TLS_PAIRING, 12000);
        if (port <= 0) {
            throw new IllegalStateException("Pairing dialog not found. Leave it open in Wireless debugging.");
        }
        Connection manager = new Connection(identity);
        try {
            boolean result = manager.pair(LOOPBACK, port, code);
            if (!result) throw new IllegalStateException("Android did not accept the pairing code.");
            return "Paired with Android's local debugging service. You can return to PhnLnk.";
        } finally {
            manager.close();
        }
    }

    String startProbe() throws Exception {
        return startProbe(null);
    }

    String startProbe(Progress listener) throws Exception {
        String key = probeKey();
        progress(listener, "Checking whether the local audio helper already responds...");
        String existing;
        try {
            existing = AudioProbeClient.request(context, "PING");
        } catch (Exception ignored) { existing = ""; }
        if (existing.startsWith("READY:V1:")) return existing;

        int configuredPort = AudioAccessState.manualPort(context);
        progress(listener, "Searching for the Wireless Debugging connection port...");
        int discoveredPort = discover(AdbMdns.SERVICE_TYPE_TLS_CONNECT, 9000);
        int port = discoveredPort > 0 ? discoveredPort : configuredPort;
        if (port <= 0) {
            throw new IllegalStateException("No Wireless Debugging connection port found. "
                    + "Keep Wireless Debugging enabled and Wi-Fi on. "
                    + "You can enter the port printed on Android's Wireless Debugging screen.");
        }
        Connection connection = new Connection(identity);
        try {
            progress(listener, "Connecting to Android's local debugging port " + port + "...");
            boolean accepted;
            try {
                accepted = connection.connect(LOOPBACK, port);
            } catch (Exception primary) {
                if (configuredPort <= 0 || configuredPort == port) throw primary;
                progress(listener, "Trying the connection port you entered manually...");
                accepted = connection.connect(LOOPBACK, configuredPort);
            }
            if (!accepted && !connection.isConnected() &&
                    configuredPort > 0 && configuredPort != port) {
                progress(listener, "Trying the connection port you entered manually...");
                accepted = connection.connect(LOOPBACK, configuredPort);
            }
            if (!accepted && !connection.isConnected())
                throw new IllegalStateException("Android rejected the saved pairing or connection port. "
                        + "Check Wireless Debugging > Paired devices and the connection port.");
            // Stage only the bundled executable, not user data or call audio.
            File external = context.getExternalFilesDir(null);
            if (external == null) throw new IllegalStateException("Phone file storage is unavailable.");
            File stage = new File(external, PROBE_ASSET);
            try (InputStream asset = context.getAssets().open(PROBE_ASSET);
                 FileOutputStream output = new FileOutputStream(stage)) {
                byte[] bytes = new byte[8192];
                int count;
                while ((count = asset.read(bytes)) != -1) output.write(bytes, 0, count);
            }
            String path = stage.getAbsolutePath();
            if (!path.matches("[A-Za-z0-9_./-]+"))
                throw new IllegalStateException("Unexpected probe path on this phone.");
            progress(listener, "Copying the bundled audio test helper into Android's shell...");
            String staged = shell(connection, "cp " + path + " " + PROBE_DEVICE_PATH
                    + " && chmod 600 " + PROBE_DEVICE_PATH + " && echo PROBE_STAGED");
            if (!staged.contains("PROBE_STAGED"))
                throw new IllegalStateException("Android could not stage its shell helper: "
                        + abbreviate(staged));
            // setsid detaches the helper from the ADB shell process group.
            // The secret is random, per installation, and never printed.
            String launch = "CLASSPATH=" + PROBE_DEVICE_PATH + " setsid nohup app_process / "
                    + MAIN_CLASS + " " + key
                    + " < /dev/null > " + PROBE_LOG + " 2>&1 & echo PROBE_STARTED";
            progress(listener, "Starting the local shell helper...");
            String started = shell(connection, launch);
            if (!started.contains("PROBE_STARTED"))
                throw new IllegalStateException("Shell process launch did not complete.");
        } finally {
            connection.close();
        }

        progress(listener, "Waiting for the audio helper to answer its local health check...");
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                String response = AudioProbeClient.request(context, "PING");
                if (response.startsWith("READY:")) return response;
            } catch (Exception ignored) { }
            Thread.sleep(300);
        }
        // If app_process exited, its local shell-owned log often gives the
        // exact Android compatibility error. Fetch only the final 500 bytes,
        // redact the private authorization key, and never log shell commands.
        String diagnostic = "";
        try {
            Connection inspect = new Connection(identity);
            try {
                if (inspect.connect(LOOPBACK, port) || inspect.isConnected()) {
                    diagnostic = shell(inspect,"tail -c 500 " + PROBE_LOG);
                }
            } finally {
                inspect.close();
            }
        } catch (Exception ignored) { }
        diagnostic = diagnostic == null ? "" : diagnostic.replace(key,"[redacted]").trim();
        if (diagnostic.length() > 220) diagnostic = diagnostic.substring(diagnostic.length()-220);
        throw new IllegalStateException(
                "The shell helper did not respond after launch."
                + (diagnostic.isEmpty() ? " Check Android Wireless Debugging."
                : " Android reported: " + diagnostic));
    }

    private static String abbreviate(String value) {
        if (value == null) return "";
        return value.length() > 160 ? value.substring(0, 160) : value;
    }

    String probeKey() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = prefs.getString("session_secret", null);
        if (key != null && key.matches("[0-9a-f]{64}")) return key;
        byte[] data = new byte[32];
        new SecureRandom().nextBytes(data);
        char[] hex = "0123456789abcdef".toCharArray();
        char[] out = new char[64];
        for (int i = 0; i < data.length; i++) {
            out[i*2] = hex[(data[i] & 255) >>> 4];
            out[i*2+1] = hex[data[i] & 15];
        }
        key = new String(out);
        if (!prefs.edit().putString("session_secret", key).commit())
            throw new IllegalStateException("Could not save shell helper authorization");
        return key;
    }

    private int discover(String serviceType, int timeoutMs) throws Exception {
        AtomicInteger result = new AtomicInteger(-1);
        CountDownLatch done = new CountDownLatch(1);
        // Android may advertise its own adbd through an address that
        // NetworkInterface.getByInetAddress does not match (e.g. scoped IPv6).
        // Discovery only supplies an ephemeral port. The actual ADB session
        // is ALWAYS opened against 127.0.0.1, never the advertised peer.
        AdbMdns mdns = new AdbMdns(context, serviceType, (InetAddress host, int port) -> {
            if (port > 0 && result.compareAndSet(-1, port)) done.countDown();
        });
        WifiManager wifi = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock multicast = null;
        if (wifi != null) {
            multicast = wifi.createMulticastLock("phnlnk-local-adb");
            multicast.setReferenceCounted(false);
            multicast.acquire();
        }
        try {
            mdns.start();
            done.await(timeoutMs, TimeUnit.MILLISECONDS);
            return result.get();
        } finally {
            try { mdns.stop(); } catch (Exception ignored) { }
            if (multicast != null && multicast.isHeld()) multicast.release();
        }
    }

    private static String shell(Connection manager, String command) throws Exception {
        // Android's ADB library can wait indefinitely for EOF even when a
        // shell command has exited. Time-limit the entire operation, including
        // openStream(), so "Connecting..." never stays on screen forever.
        ExecutorService timeout = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r,"PhnLnk-ADB-command");
            thread.setDaemon(true);
            return thread;
        });
        Future<String> future = timeout.submit(() -> {
            AdbStream stream = manager.openStream("shell:" + command);
            try (InputStream input = stream.openInputStream()) {
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                byte[] bytes = new byte[1024];
                int n;
                while ((n = input.read(bytes)) != -1 && result.size() < 16000) {
                    result.write(bytes,0,n);
                }
                return result.toString("UTF-8");
            } finally {
                try { stream.close(); } catch (Exception ignored) { }
            }
        });
        try {
            return future.get(14000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutError) {
            future.cancel(true);
            throw new IllegalStateException(
                    "Android's debugging shell stopped responding. "
                    + "Toggle Wireless Debugging off and on, then retry.");
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new IllegalStateException("Android shell command failed",cause);
        } finally {
            timeout.shutdownNow();
        }
    }

    private static final class Connection extends AbsAdbConnectionManager {
        private final KeyIdentity id;
        Connection(KeyIdentity id) {
            this.id = id;
            setApi(Build.VERSION.SDK_INT);
            setTimeout(10, TimeUnit.SECONDS);
        }
        @Override protected PrivateKey getPrivateKey() {return id.privateKey;}
        @Override protected Certificate getCertificate() {return id.certificate;}
        @Override protected String getDeviceName() {return "PhnLnk";}
    }

    private static final class KeyIdentity {
        final PrivateKey privateKey;
        final Certificate certificate;
        KeyIdentity(PrivateKey privateKey, Certificate certificate) {
            this.privateKey = privateKey;
            this.certificate = certificate;
        }
        static synchronized KeyIdentity loadOrCreate(Context context) throws Exception {
            File keyFile = new File(context.getFilesDir(), KEY_FILE);
            File certFile = new File(context.getFilesDir(), CERT_FILE);
            if (keyFile.isFile() && certFile.isFile()) {
                try {
                    PrivateKey key = KeyFactory.getInstance("RSA")
                            .generatePrivate(new PKCS8EncodedKeySpec(
                                    java.nio.file.Files.readAllBytes(keyFile.toPath())));
                    Certificate cert = CertificateFactory.getInstance("X.509")
                            .generateCertificate(new ByteArrayInputStream(
                                    java.nio.file.Files.readAllBytes(certFile.toPath())));
                    return new KeyIdentity(key, cert);
                } catch (Exception error) {
                    throw new IllegalStateException(
                            "The saved ADB identity is unreadable. Reinstall or clear app data to pair again.",
                            error);
                }
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, new SecureRandom());
            KeyPair pair = generator.generateKeyPair();
            long now = System.currentTimeMillis();
            X500Name name = new X500Name(new X500Principal("CN=PhnLnk, O=PhnLnk, C=US").getName());
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    name, new BigInteger(64,new SecureRandom()),
                    new Date(now - 86400000L),
                    new Date(now + 20L * 365 * 86400000L),
                    name, pair.getPublic());
            byte[] encoded = builder.build(
                    new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())).getEncoded();
            Certificate certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(encoded));
            try (FileOutputStream output = new FileOutputStream(keyFile)) {
                output.write(pair.getPrivate().getEncoded());
            }
            try (FileOutputStream output = new FileOutputStream(certFile)) {
                output.write(certificate.getEncoded());
            }
            return new KeyIdentity(pair.getPrivate(), certificate);
        }
    }
}
