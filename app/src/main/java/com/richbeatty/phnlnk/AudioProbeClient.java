package com.richbeatty.phnlnk;

import android.content.Context;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.Socket;

/** Authentication to the local shell-UID audio helper. Never exposed to LAN. */
final class AudioProbeClient {
    private AudioProbeClient() {}

    static String request(Context context, String command) throws Exception {
        if (!("PING".equals(command) || "CAPTURE".equals(command) || "INJECT".equals(command)
                || "INJECT:REDIRECT".equals(command) || "INJECT:TELEPHONY".equals(command)
                || "STOP".equals(command)))
            throw new IllegalArgumentException("Unknown diagnostic request");
        String token = context.getSharedPreferences(
                "phnlnk_audio_bootstrap", Context.MODE_PRIVATE)
                .getString("session_secret", "");
        if (!token.matches("[0-9a-f]{64}"))
            throw new IllegalStateException("Start the audio helper first.");
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(
                    InetAddress.getByName("127.0.0.1"), 47327), 1200);
            socket.setSoTimeout(7000);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            DataInputStream input = new DataInputStream(socket.getInputStream());
            output.writeUTF(token);
            output.writeUTF(command);
            output.flush();
            String result = input.readUTF();
            if (result.startsWith("ERROR:")) throw new IllegalStateException(result.substring(6));
            return result;
        }
    }
}
