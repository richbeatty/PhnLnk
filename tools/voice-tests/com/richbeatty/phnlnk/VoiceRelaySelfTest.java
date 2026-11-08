package com.richbeatty.phnlnk;

import android.content.Context;
import android.telephony.TelephonyManager;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Production relay + production crypto; only Android state and the audio HAL are substituted. */
public final class VoiceRelaySelfTest {
    private static final Context CONTEXT = new Context();
    private static int passed;
    private interface Test { void run() throws Exception; }
    private interface Helper { void run(Socket socket) throws Exception; }

    private static void check(boolean condition, String error) {
        if (!condition) throw new AssertionError(error);
    }

    private static void test(String name, Test test) throws Exception {
        TelephonyManager.state = 0;
        AudioAccessState.enabled = true;
        VoiceRelayServer relay = new VoiceRelayServer(CONTEXT);
        relay.start();
        try {
            test.run();
            System.out.println("PASS " + name); passed++;
        } finally {
            relay.close();
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (VoiceRelayServer.isBusy() && System.nanoTime() < end) Thread.yield();
            check(!VoiceRelayServer.isBusy(), "Relay failed to release session ownership");
        }
    }

    private static Socket connect() throws Exception {
        Socket socket = new Socket("127.0.0.1", VoiceSecureTransport.PORT);
        socket.setSoTimeout(3000);
        return socket;
    }

    private static String banner(VoiceSecureTransport.Session session) throws Exception {
        byte[] frame = session.receive();
        int n = (frame[0]&255) | ((frame[1]&255)<<8);
        check(n > 0 && n <= frame.length-2, "Invalid relay control frame");
        return new String(frame, 2, n, StandardCharsets.UTF_8);
    }

    private static void expectBanner(String text) throws Exception {
        try (Socket socket = connect()) {
            String actual = banner(VoiceSecureTransport.onClient(CONTEXT, socket));
            check(actual.contains(text), "Expected " + text + ", got " + actual);
        }
    }

    private static void withHelper(Helper helper, Test client) throws Exception {
        try (ServerSocket listener = new ServerSocket(47327, 4, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(3000);
            ExecutorService worker = Executors.newSingleThreadExecutor();
            Future<?> task = worker.submit(() -> {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(3000);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    check(in.readUTF().equals("a".repeat(64)), "Invalid helper secret");
                    check(in.readUTF().equals("STREAM:AUTO:AUTO"), "Invalid stream command");
                    helper.run(socket);
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            try { client.run(); task.get(5, TimeUnit.SECONDS); }
            finally { worker.shutdownNow(); }
        }
    }

    public static void main(String[] args) throws Exception {
        test("no call gets authenticated startup error", () -> expectBanner("ERROR:NO_CALL"));
        test("disabled audio access gets explicit error", () -> {
            TelephonyManager.state = 2; AudioAccessState.enabled = false;
            expectBanner("ERROR:ACCESS_OFF");
        });
        test("helper connection exception sends error before closing socket", () -> {
            TelephonyManager.state = 2;
            expectBanner("ERROR:CONNECT_SHELL_HELPER");
        });
        test("helper early EOF is reported in encrypted startup error", () -> {
            TelephonyManager.state = 2;
            withHelper(socket -> socket.shutdownOutput(), () -> expectBanner("HELPER_CLOSED"));
        });
        test("helper initialization refusal is preserved", () -> {
            TelephonyManager.state = 2;
            withHelper(socket -> {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.writeUTF("ERROR:AUDIO_INIT:unsupported test backend"); out.flush();
            }, () -> expectBanner("ERROR:AUDIO_INIT:unsupported test backend"));
        });
        test("unauthorized stalled handshake does not occupy voice session", () -> {
            try (Socket stalled = connect()) {
                new DataInputStream(stalled.getInputStream()).readFully(new byte[36]);
                expectBanner("ERROR:NO_CALL");
            }
        });
        test("authentication worker quota rejects excess sockets", () -> {
            java.util.ArrayList<Socket> stalled = new java.util.ArrayList<>();
            try {
                for (int i = 0; i < 4; i++) {
                    Socket socket = connect(); stalled.add(socket);
                    new DataInputStream(socket.getInputStream()).readFully(new byte[36]);
                }
                try (Socket excess = connect()) {
                    check(excess.getInputStream().read() == -1, "Excess authentication socket was not rejected");
                }
                check(!VoiceRelayServer.isBusy(), "Unauthenticated quota clients acquired voice ownership");
            } finally { for (Socket socket : stalled) socket.close(); }
        });
        test("absolute handshake deadline closes a dribbling proof", () -> {
            try (Socket socket = connect()) {
                new DataInputStream(socket.getInputStream()).readFully(new byte[36]);
                socket.setSoTimeout(13000);
                java.util.concurrent.ScheduledExecutorService drip = Executors.newSingleThreadScheduledExecutor();
                try {
                    // 48 proof/nonce bytes at 250 ms each exceed the absolute
                    // 9 s budget, but never trigger a per-read 9 s SO_TIMEOUT.
                    drip.scheduleAtFixedRate(() -> {
                        try { socket.getOutputStream().write(0); socket.getOutputStream().flush(); }
                        catch (java.io.IOException closed) { }
                    }, 0, 250, TimeUnit.MILLISECONDS);
                    try { check(socket.getInputStream().read() == -1, "Unexpected pre-auth application data"); }
                    catch (java.net.SocketException closed) { /* reset is also a terminal close */ }
                    check(!VoiceRelayServer.isBusy(), "Dribbling client acquired voice ownership");
                } finally { drip.shutdownNow(); }
            }
        });
        test("duplex relay, encrypted busy refusal and revocation close sockets", () -> {
            TelephonyManager.state = 2;
            byte[] pcm = new byte[640]; Arrays.fill(pcm, (byte)37);
            java.util.concurrent.CountDownLatch uplinkDelivered = new java.util.concurrent.CountDownLatch(1);
            withHelper(socket -> {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.writeUTF("STREAM_READY:DOWN=TEST:UP=TEST"); out.flush();
                out.write(pcm); out.flush();
                byte[] up = new byte[640]; new DataInputStream(socket.getInputStream()).readFully(up);
                check(Arrays.equals(up, pcm), "Relay corrupted uplink");
                uplinkDelivered.countDown();
                check(socket.getInputStream().read() == -1, "Revocation did not close helper socket");
            }, () -> {
                try (Socket active = connect(); Socket pending = connect()) {
                    VoiceSecureTransport.Session wire = VoiceSecureTransport.onClient(CONTEXT, active);
                    check(banner(wire).startsWith("PHNLNK:READY:"), "Missing READY");
                    check(Arrays.equals(wire.receive(), pcm), "Relay corrupted downlink");
                    wire.send(pcm);
                    check(uplinkDelivered.await(3, TimeUnit.SECONDS), "Uplink was not delivered");
                    new DataInputStream(pending.getInputStream()).readFully(new byte[36]);
                    expectBanner("ERROR:HOST_BUSY");
                    check(VoiceRelayServer.lastStage().equals("STREAMING"),
                            "Competing authentication overwrote active session stage");
                    VoiceRelayServer.disconnectActive();
                    try { wire.receive(); throw new AssertionError("Revoked media remained open"); }
                    catch (java.io.IOException expected) {
                        check(expected.getMessage().contains("VOICE_MEDIA_CLOSED"), "Wrong revocation error");
                    }
                    check(pending.getInputStream().read() == -1, "Revocation missed pending authentication");
                }
            });
        });
        check(passed == 9, "Not all relay tests ran");
        System.out.println("All " + passed + " production relay tests passed.");
    }
}
