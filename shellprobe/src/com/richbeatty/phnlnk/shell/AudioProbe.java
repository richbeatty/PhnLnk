package com.richbeatty.phnlnk.shell;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Process;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shell-UID, loopback-only, explicit live-call capability probe.
 *
 * NO recording is written. CAPTURE only calculates an amplitude measurement.
 * INJECT emits one short quiet tone after the user explicitly requests it.
 * This class runs with Android's SHELL UID from app_process, never as an
 * ordinary app's process. The companion is NOT connected to audio yet.
 */
public final class AudioProbe {
    private static final int PORT = 47327;
    private static final int RATE = 16000;
    private static final int FRAME_BYTES = 640;
    private static final int CAPTURE_MS = 1800;
    private static final int TONE_MS = 550;
    private final byte[] secret;
    private volatile boolean running = true;

    private AudioProbe(byte[] key) {
        secret = key;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].matches("[0-9a-f]{64}")) {
            System.err.println("Missing probe authentication key");
            return;
        }
        if (Process.myUid() != 2000) {
            System.err.println("Refusing to run without shell identity");
            return;
        }
        AudioProbe probe = new AudioProbe(args[0].getBytes(StandardCharsets.US_ASCII));
        try (ServerSocket server = new ServerSocket(PORT, 2,
                InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(0);
            while (probe.running) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(7000);
                    probe.handle(socket);
                } catch (Exception e) {
                    System.err.println("Probe request: " + e.getClass().getSimpleName());
                }
            }
        }
    }

    private void handle(Socket socket) throws Exception {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        String candidate = input.readUTF();
        if (!MessageDigest.isEqual(secret, candidate.getBytes(StandardCharsets.US_ASCII))) {
            output.writeUTF("ERROR:Invalid authorization");
            output.flush();
            return;
        }
        String request = input.readUTF();
        String result;
        switch (request) {
            case "PING": result = "READY:V1:shell UID " + Process.myUid(); break;
            case "CAPTURE": result = captureAmplitude(); break;
            case "INJECT": result = injectTestTone(); break;
            case "STOP": result = "STOPPED"; running = false; break;
            default: result = "ERROR:Unknown request";
        }
        output.writeUTF(result);
        output.flush();
    }

    private static String captureAmplitude() {
        AudioRecord record = null;
        try {
            int min = AudioRecord.getMinBufferSize(
                    RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) return "CAPTURE_ERROR:No valid audio buffer";
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_CALL, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(min * 2, FRAME_BYTES * 8));
            if (record.getState() != AudioRecord.STATE_INITIALIZED)
                return "CAPTURE_ERROR:VOICE_CALL source unavailable";
            record.startRecording();
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                return "CAPTURE_ERROR:Could not start VOICE_CALL capture";
            short[] pcm = new short[FRAME_BYTES / 2];
            long energy = 0;
            long samples = 0;
            int peak = 0;
            long deadline = System.nanoTime() + CAPTURE_MS * 1000000L;
            while (System.nanoTime() < deadline && samples < RATE * 3) {
                int count = record.read(pcm, 0, pcm.length, AudioRecord.READ_BLOCKING);
                if (count < 0) return "CAPTURE_ERROR:AudioRecord read " + count;
                for (int i = 0; i < count; i++) {
                    int value = pcm[i];
                    int abs = Math.abs(value);
                    if (abs > peak) peak = abs;
                    energy += (long)value * value;
                }
                samples += count;
            }
            if (samples < RATE / 3) return "CAPTURE_ERROR:Too little audio captured";
            long rms = (long)Math.sqrt((double)energy / samples);
            // We do not claim to hear the caller just because the stream has
            // nonzero samples. That must be verified during a live test call.
            return "CAPTURE:Samples=" + samples + ",RMS=" + rms + ",Peak=" + peak
                    + (rms < 80 ? ",SILENT_OR_MUTED" : ",SIGNAL_DETECTED");
        } catch (SecurityException e) {
            return "CAPTURE_ERROR:Android denied shell call audio: " + e.getClass().getSimpleName();
        } catch (Exception e) {
            return "CAPTURE_ERROR:" + e.getClass().getSimpleName() + ": " + safe(e.getMessage());
        } finally {
            if (record != null) {
                try {record.stop();} catch (Exception ignored) {}
                record.release();
            }
        }
    }

    private static String injectTestTone() {
        AudioTrack track = null;
        try {
            // A shell process can create a system Context, unlike an ordinary
            // application. This is a hidden API and device compatibility is
            // explicitly under test, never assumed.
            Class<?> threadType = Class.forName("android.app.ActivityThread");
            Object thread = threadType.getMethod("systemMain").invoke(null);
            Context context = (Context) threadType.getMethod("getSystemContext").invoke(thread);
            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (manager == null) return "INJECT_ERROR:AudioManager unavailable";
            AudioDeviceInfo telephony = null;
            for (AudioDeviceInfo d : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (d.getType() == AudioDeviceInfo.TYPE_TELEPHONY) {
                    telephony = d;
                    break;
                }
            }
            if (telephony == null)
                return "INJECT_ERROR:No telephony uplink device exposed";
            int min = AudioTrack.getMinBufferSize(
                    RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) return "INJECT_ERROR:No valid audio output buffer";
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(Math.max(min * 2, FRAME_BYTES * 8))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED)
                return "INJECT_ERROR:AudioTrack could not initialize";
            if (!track.setPreferredDevice(telephony))
                return "INJECT_ERROR:Android refused telephony route";
            track.play();
            if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
                return "INJECT_ERROR:AudioTrack did not play";
            int count = RATE * TONE_MS / 1000;
            short[] tone = new short[count];
            for (int i = 0; i < tone.length; i++) {
                // 660 Hz, deliberately quiet (~-25 dBFS) to avoid blasting ear.
                tone[i] = (short)(1800 * Math.sin(2 * Math.PI * 660 * i / RATE));
            }
            int wrote = 0;
            while (wrote < count) {
                int n = track.write(tone, wrote, count - wrote, AudioTrack.WRITE_BLOCKING);
                if (n <= 0) return "INJECT_ERROR:Audio write failed " + n;
                wrote += n;
            }
            // Give the output buffer time to drain so the test tone can reach
            // the route before we release the track.
            try {Thread.sleep(TONE_MS + 150);} catch (InterruptedException ignored) {}
            return "INJECT:Output routed to TYPE_TELEPHONY; caller audibility NOT confirmed";
        } catch (Exception e) {
            return "INJECT_ERROR:" + e.getClass().getSimpleName() + ": " + safe(e.getMessage());
        } finally {
            if (track != null) {
                try {track.pause();track.flush();track.stop();} catch (Exception ignored) {}
                track.release();
            }
        }
    }

    private static String safe(String text) {
        if (text == null) return "";
        return text.length() <= 110 ? text : text.substring(0,110);
    }
}
