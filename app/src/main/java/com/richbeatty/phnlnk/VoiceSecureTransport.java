package com.richbeatty.phnlnk;

import android.content.Context;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Audio-only authenticated encrypted TCP protocol for a paired LAN device.
 * The existing HTTP call-control channel is separate and is NOT encrypted.
 * Never expose the existing HTTP listener to the public Internet.
 *
 * Handshake uses HMAC-SHA256 proofs based on SHA-256 of the pairing token,
 * so the raw token and the secret itself are never sent in the clear here.
 * Media uses separate AES-256-GCM traffic keys and monotonic counters.
 */
final class VoiceSecureTransport {
    static final int PORT=47322;
    static final int FRAME_BYTES=640;
    private static final int MAGIC=0x50484E31; // PHN1
    private static final SecureRandom RANDOM=new SecureRandom();

    private VoiceSecureTransport() { }

    static byte[] fromHex(String text) {
        if(text==null || !text.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid stored pairing fingerprint");
        byte[] out=new byte[32];
        for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(text.substring(2*i,2*i+2),16);
        return out;
    }

    static byte[] proof(byte[] key,String label,byte[] serverNonce,byte[] clientNonce)
            throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key,"HmacSHA256"));
        mac.update(("PhnLnk-v1-"+label+":").getBytes(StandardCharsets.US_ASCII));
        mac.update(serverNonce);
        mac.update(clientNonce);
        return mac.doFinal();
    }

    private static byte[] tokenKey(Context ctx) throws Exception {
        String token=RemoteClient.voiceToken(ctx);
        if(!token.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalStateException("Pair the phone before enabling voice");
        return MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.US_ASCII));
    }

    static Session onClient(Context ctx,Socket socket) throws Exception {
        socket.setSoTimeout(9000);
        DataInputStream input=new DataInputStream(socket.getInputStream());
        DataOutputStream output=new DataOutputStream(socket.getOutputStream());
        if(input.readInt()!=MAGIC)throw new IOException("Host does not support encrypted voice");
        byte[] serverNonce=new byte[32];
        input.readFully(serverNonce);
        byte[] clientNonce=new byte[16];RANDOM.nextBytes(clientNonce);
        byte[] key=tokenKey(ctx);
        byte[] clientProof=proof(key,"client",serverNonce,clientNonce);
        output.write(clientNonce);output.write(clientProof);output.flush();
        byte[] expectedServer=proof(key,"server",serverNonce,clientNonce);
        byte[] serverProof=new byte[32];
        input.readFully(serverProof);
        if(!MessageDigest.isEqual(expectedServer,serverProof))
            throw new SecurityException("Host rejected the saved pairing for voice");
        socket.setSoTimeout(12000);
        return new Session(input,output,
                proof(key,"client-to-host",serverNonce,clientNonce),
                proof(key,"host-to-client",serverNonce,clientNonce),
                0x434C4945,0x484F5354);
    }

    static Session onHost(Context ctx,Socket socket) throws Exception {
        socket.setSoTimeout(9000);
        DataInputStream input=new DataInputStream(socket.getInputStream());
        DataOutputStream output=new DataOutputStream(socket.getOutputStream());
        byte[] serverNonce=new byte[32];RANDOM.nextBytes(serverNonce);
        output.writeInt(MAGIC);output.write(serverNonce);output.flush();
        byte[] clientNonce=new byte[16],clientProof=new byte[32];
        input.readFully(clientNonce);input.readFully(clientProof);
        byte[] key=PairingManager.get(ctx).findVoiceKey(serverNonce,clientNonce,clientProof);
        if(key==null)throw new SecurityException("Voice device isn't paired");
        output.write(proof(key,"server",serverNonce,clientNonce));output.flush();
        socket.setSoTimeout(12000);
        return new Session(input,output,
                proof(key,"host-to-client",serverNonce,clientNonce),
                proof(key,"client-to-host",serverNonce,clientNonce),
                0x484F5354,0x434C4945);
    }

    static final class Session {
        private final DataInputStream input;
        private final DataOutputStream output;
        private final byte[] txKey,rxKey;
        private final int txPrefix,rxPrefix;
        private long sendSequence,receiveSequence;

        private Session(DataInputStream input,DataOutputStream output,
                        byte[] txKey,byte[] rxKey,int txPrefix,int rxPrefix) {
            this.input=input;this.output=output;this.txKey=txKey;this.rxKey=rxKey;
            this.txPrefix=txPrefix;this.rxPrefix=rxPrefix;
        }

        void send(byte[] pcm) throws Exception {
            if(pcm.length!=FRAME_BYTES)throw new IOException("Unexpected PCM frame size");
            if(sendSequence>=Long.MAX_VALUE-1)throw new IOException("Voice session expired");
            long seq=sendSequence++;
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(txKey,"AES"),
                    new GCMParameterSpec(128,nonce(txPrefix,seq)));
            cipher.updateAAD(ByteBuffer.allocate(8).putLong(seq).array());
            byte[] encrypted=cipher.doFinal(pcm);
            synchronized(output) {
                output.writeLong(seq);
                output.writeShort(encrypted.length);
                output.write(encrypted);
                output.flush();
            }
        }

        byte[] receive() throws Exception {
            long seq=input.readLong();
            int length=input.readUnsignedShort();
            if(seq!=receiveSequence || length!=FRAME_BYTES+16)
                throw new SecurityException("Voice packet sequence or size invalid");
            byte[] encrypted=new byte[length];
            input.readFully(encrypted);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(rxKey,"AES"),
                    new GCMParameterSpec(128,nonce(rxPrefix,seq)));
            cipher.updateAAD(ByteBuffer.allocate(8).putLong(seq).array());
            byte[] pcm=cipher.doFinal(encrypted);
            receiveSequence++;
            return pcm;
        }
    }

    private static byte[] nonce(int direction,long counter) {
        return ByteBuffer.allocate(12).putInt(direction).putLong(counter).array();
    }
}
