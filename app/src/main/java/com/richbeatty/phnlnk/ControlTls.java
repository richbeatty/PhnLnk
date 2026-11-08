package com.richbeatty.phnlnk;

import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** TLS primitives shared by Android and real JVM socket tests. No global trust overrides. */
final class ControlTls {
    private ControlTls() { }

    static String fingerprint(X509Certificate certificate) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder text = new StringBuilder(64);
        for (byte b : hash) text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return text.toString();
    }

    static String requirePin(String pin) {
        if (pin == null || !pin.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Verify the main phone's security fingerprint first");
        return pin;
    }

    static String displayPin(String pin) {
        return requirePin(pin).replaceAll("(.{8})(?!$)", "$1 ");
    }

    static SSLContext serverContext(KeyStore identity, char[] password) throws Exception {
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(identity, password);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(keys.getKeyManagers(), null, null);
        return tls;
    }

    static SSLContext pinnedContext(String pin) throws Exception {
        String expected = requirePin(pin);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(null, new TrustManager[]{new X509TrustManager() {
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            @Override public void checkClientTrusted(X509Certificate[] chain, String auth)
                    throws CertificateException { throw new CertificateException("Client certificates not used"); }
            @Override public void checkServerTrusted(X509Certificate[] chain, String auth)
                    throws CertificateException {
                try {
                    if (chain == null || chain.length == 0 || !samePin(expected, fingerprint(chain[0])))
                        throw new CertificateException("HOST_IDENTITY_CHANGED: Main phone certificate does not match");
                    chain[0].checkValidity();
                } catch (CertificateException error) { throw error; }
                catch (Exception error) { throw new CertificateException("Cannot verify main phone identity", error); }
            }
        }}, null);
        return tls;
    }

    private static boolean samePin(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    // The exact certificate pin is the endpoint identity; addresses may change with Wi-Fi/VPN.
    // This verifier supplements the pinned TrustManager, never bypasses it.
    static boolean matches(String pin, SSLSession session) {
        try { return samePin(requirePin(pin), fingerprint((X509Certificate)session.getPeerCertificates()[0])); }
        catch (Exception error) { return false; }
    }

    /** Only for showing a candidate to the human. Sends NO HTTP, code, token or call data. */
    static String discoverUntrustedFingerprint(String host, int port) throws Exception {
        SSLContext discovery = SSLContext.getInstance("TLS");
        discovery.init(null, new TrustManager[]{new X509TrustManager() {
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            @Override public void checkClientTrusted(X509Certificate[] chain, String auth)
                    throws CertificateException { throw new CertificateException("Not a client verifier"); }
            @Override public void checkServerTrusted(X509Certificate[] chain, String auth)
                    throws CertificateException {
                if (chain == null || chain.length == 0) throw new CertificateException("Missing certificate");
            }
        }}, null);
        try (SSLSocket socket = (SSLSocket)discovery.getSocketFactory().createSocket()) {
            socket.connect(new InetSocketAddress(host, port), 4000);
            socket.setSoTimeout(4000);
            socket.startHandshake();
            return fingerprint((X509Certificate)socket.getSession().getPeerCertificates()[0]);
        }
    }
}
