package com.richbeatty.phnlnk;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Date;
import javax.net.ssl.SSLServerSocket;
import javax.security.auth.x500.X500Principal;

/** Per-install host TLS identity. APK signing and saved bearer tokens are separate. */
final class HostTlsIdentity {
    private static final String LEGACY_ALIAS = "phnlnk_control_tls_v1";
    private static final String TLS_ALIAS = "phnlnk_control_tls_v2";
    private HostTlsIdentity() { }

    private static KeyStore store() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }

    private static void generate() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore");
        generator.initialize(new KeyGenParameterSpec.Builder(TLS_ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                // Conscrypt hashes TLS transcripts natively, then delegates opaque
                // EC signing to NONEwithECDSA. SHA256 alone cannot authorize that.
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                .setCertificateSubject(new X500Principal("CN=PhnLnk host"))
                .setCertificateSerialNumber(new BigInteger(128, new SecureRandom()).add(BigInteger.ONE))
                .setCertificateNotBefore(new Date(System.currentTimeMillis() - 86400000L))
                .setCertificateNotAfter(new Date(System.currentTimeMillis() + 10L*365*86400000L))
                .build());
        generator.generateKeyPair();
    }

    private static String alias(KeyStore store) throws Exception {
        if (store.containsAlias(TLS_ALIAS)) return TLS_ALIAS;
        if (store.containsAlias(LEGACY_ALIAS)) return LEGACY_ALIAS;
        generate(); // First installation only. Never replace an existing identity here.
        return TLS_ALIAS;
    }

    static synchronized boolean needsRepair() throws Exception {
        KeyStore store = store();
        String alias = alias(store);
        PrivateKey key = (PrivateKey)store.getKey(alias, null);
        KeyInfo info = KeyFactory.getInstance(key.getAlgorithm(), "AndroidKeyStore")
                .getKeySpec(key, KeyInfo.class);
        return !Arrays.asList(info.getDigests()).contains(KeyProperties.DIGEST_NONE);
    }

    /** Call ONLY following explicit host-side confirmation. Retains legacy key and all tokens. */
    static synchronized void repairAfterConfirmation() throws Exception {
        KeyStore store = store();
        if (store.containsAlias(TLS_ALIAS)) {
            if (needsRepair()) throw new IllegalStateException("Existing TLS identity needs independent diagnosis");
            return;
        }
        generate();
    }

    static synchronized String fingerprint() throws Exception {
        KeyStore store = store();
        return ControlTls.fingerprint((X509Certificate)store.getCertificate(alias(store)));
    }

    static synchronized SSLServerSocket listener() throws Exception {
        if (needsRepair()) throw new IllegalStateException(
                "HOST_TLS_KEY_NEEDS_REPAIR: Tap Repair encrypted connection on the main phone. Saved pairings are retained.");
        KeyStore store=store();
        SSLServerSocket socket = (SSLServerSocket)ControlTls.serverContext(store, null, alias(store))
                .getServerSocketFactory().createServerSocket();
        socket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        return socket;
    }
}
