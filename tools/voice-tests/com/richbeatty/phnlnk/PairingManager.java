package com.richbeatty.phnlnk;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Test-only saved host authorization. Never compile into an Android APK. */
final class PairingManager {
    static String HOST_TOKEN=RemoteClient.TOKEN;
    static PairingManager get(Context ignored) {return new PairingManager();}
    byte[] findVoiceKey(byte[] serverNonce,byte[] clientNonce,byte[] proof) throws Exception {
        byte[] key=MessageDigest.getInstance("SHA-256")
                .digest(HOST_TOKEN.getBytes(StandardCharsets.US_ASCII));
        byte[] expected=VoiceSecureTransport.proof(key,"client",serverNonce,clientNonce);
        return MessageDigest.isEqual(expected,proof)?key:null;
    }
}
