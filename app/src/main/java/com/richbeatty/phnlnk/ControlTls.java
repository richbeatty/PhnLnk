package com.richbeatty.phnlnk;

import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.SSLEngine;
import java.security.PrivateKey;
import java.security.Principal;
import java.net.Socket;

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

    /** Serve only the explicitly selected identity, even when a legacy key is retained. */
    static SSLContext serverContext(KeyStore identity, char[] password, String alias) throws Exception {
        final PrivateKey key=(PrivateKey)identity.getKey(alias,password);
        java.security.cert.Certificate[] stored=identity.getCertificateChain(alias);
        if(key==null || stored==null || stored.length==0)
            throw new IllegalStateException("Host TLS identity is incomplete");
        final X509Certificate[] chain=new X509Certificate[stored.length];
        for(int i=0;i<stored.length;i++)chain[i]=(X509Certificate)stored[i];
        X509ExtendedKeyManager selected=new X509ExtendedKeyManager(){
            private boolean supports(String type){
                if(type==null)return false;
                String[] parts=type.split("_",2);
                return parts[0].equals(key.getAlgorithm()) && (parts.length==1 ||
                        chain[0].getSigAlgName().toUpperCase(java.util.Locale.ROOT).contains(parts[1]));
            }
            @Override public String[] getServerAliases(String type,Principal[] issuers){
                return supports(type)?new String[]{alias}:null;
            }
            @Override public String chooseServerAlias(String type,Principal[] issuers,Socket socket){
                return supports(type)?alias:null;
            }
            @Override public String chooseEngineServerAlias(String type,Principal[] issuers,SSLEngine engine){
                return supports(type)?alias:null;
            }
            @Override public X509Certificate[] getCertificateChain(String name){
                return alias.equals(name)?chain.clone():null;
            }
            @Override public PrivateKey getPrivateKey(String name){return alias.equals(name)?key:null;}
            @Override public String[] getClientAliases(String type,Principal[] issuers){return null;}
            @Override public String chooseClientAlias(String[] types,Principal[] issuers,Socket socket){return null;}
        };
        SSLContext tls=SSLContext.getInstance("TLS");
        tls.init(new javax.net.ssl.KeyManager[]{selected},null,null);
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
        } catch(java.io.IOException error) {
            throw new java.io.IOException("HOST_TLS_DISCOVERY: Could not read the main phone's certificate ("
                    +error.getClass().getSimpleName()+"). Check its encrypted connection status. No pairing code has been sent.",error);
        }
    }
}
