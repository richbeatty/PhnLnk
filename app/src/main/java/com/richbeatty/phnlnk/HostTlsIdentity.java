package com.richbeatty.phnlnk;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import javax.net.ssl.SSLServerSocket;
import javax.security.auth.x500.X500Principal;

/** Per-install host identity, separate from the permanent APK signing key. */
final class HostTlsIdentity {
    private static final String ALIAS = "phnlnk_control_tls_v1";
    private HostTlsIdentity() { }

    private static synchronized KeyStore identity() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,
                    "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setCertificateSubject(new X500Principal("CN=PhnLnk host"))
                    .setCertificateSerialNumber(new BigInteger(128, new SecureRandom()).add(BigInteger.ONE))
                    .setCertificateNotBefore(new Date(System.currentTimeMillis() - 86400000L))
                    .setCertificateNotAfter(new Date(System.currentTimeMillis() + 10L*365*86400000L))
                    .build());
            generator.generateKeyPair();
        }
        return store;
    }

    static String fingerprint() throws Exception {
        return ControlTls.fingerprint((X509Certificate)identity().getCertificate(ALIAS));
    }

    static SSLServerSocket listener() throws Exception {
        SSLServerSocket socket = (SSLServerSocket)ControlTls.serverContext(identity(), null)
                .getServerSocketFactory().createServerSocket();
        socket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        return socket;
    }
}
