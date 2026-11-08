package com.richbeatty.phnlnk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * No hidden "working voice" indicator: tests explicitly distinguish
 * capturing signal from proving the caller is audible, and route selection
 * from a caller confirming the injected test tone.
 */
public final class HostAudioActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status;
    private Button capture;
    private Button inject;
    private Button launch;
    private volatile boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int space = dp(18);
        root.setPadding(space,space,space,space);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(text("PhnLnk audio test", 28));
        root.addView(text("One phone, no extra hardware or Shizuku. This checks whether your phone's software permits live cellular audio capture and injection.",14));

        status = text("",15);
        root.addView(status);
        root.addView(text("1. Enable Android Wireless Debugging",20));
        root.addView(text("Developer options > USB debugging and Wireless debugging. Use a trusted Wi-Fi network. This grants a local Android shell permission and has security implications. You can revoke the pairing in Wireless debugging settings.",14));
        root.addView(button("Open Developer options", () -> {
            try {startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}
            catch (Exception e) {status.setText("Open Settings > System > Developer options manually.");}
        }));

        root.addView(text("2. Pair PhnLnk with this phone",20));
        root.addView(text("Tap the button, then in Settings > Wireless debugging choose 'Pair device with pairing code'. Leave that dialog open. Swipe down the notification shade and enter its six digits in the PhnLnk notification. This is one-time pairing.",14));
        root.addView(button("Show pairing notification", () -> {
            try {
                Intent start = new Intent(this, AudioPairService.class).setAction(AudioPairService.START);
                startForegroundService(start);
                status.setText("Pairing notification enabled. Open the Android pairing-code dialog.");
            } catch (Exception e) {
                status.setText("Could not start pairing: " + safe(e.getMessage()));
            }
        }));

        root.addView(text("3. Check the shell audio helper",20));
        root.addView(text("After pairing, return here. This installs only PhnLnk's own local test helper and checks its Android shell identity. The helper binds to 127.0.0.1 and needs a random secret. It does not store or send any call audio.",14));
        launch = button("Start local audio test helper", () -> runTask("Starting helper", () -> {
            String answer = new HostAdb(this).startProbe();
            return "Local helper: " + answer;
        }));
        root.addView(launch);

        root.addView(text("4. Test during a real call",20));
        root.addView(text("Call a trusted person or automated line using the normal Phone app and keep the call connected. These tests process only live samples and never save a recording. For the tone test, inform the other person first.",14));
        capture = button("Test receiving caller audio", () -> {
            new AlertDialog.Builder(this)
                    .setTitle("Check live call audio?")
                    .setMessage("This samples about two seconds of active cellular call audio to measure signal level. It does not save the sound. Only run this on a call you are authorized to test.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Test", (d,w) -> runTask("Testing downlink", () ->
                            AudioProbeClient.request(this, "CAPTURE")))
                    .show();
        });
        root.addView(capture);

        inject = button("Send short test tone into call", () -> {
            new AlertDialog.Builder(this)
                    .setTitle("Send a short test tone?")
                    .setMessage("Confirm the other caller agrees to hear a quiet half-second beep. This tests the cellular audio uplink route. Android may report the track is routed without actually transmitting the tone. Ask the other person what they heard.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Send tone", (d,w) -> runTask("Testing uplink", () ->
                            AudioProbeClient.request(this, "INJECT")))
                    .show();
        });
        root.addView(inject);

        root.addView(text("What the results mean",20));
        root.addView(text("READY means the local helper launched as Android shell. CAPTURE with a nonzero level means audio samples arrived, but not necessarily the remote caller. INJECT means the audio route accepted a test tone, not that the caller heard it. Real two-way conversation requires both tests to work, then a secure bridge to your tablet.",13));
        root.addView(text("PhnLnk does not yet relay your voice to the secondary device. This screen exists to identify whether the required audio path actually works on this phone before claiming it does. Do not use experimental calling for emergencies.",13));
        updateStatus("Not tested. No extra hardware required.");
    }

    @Override protected void onResume() {
        super.onResume();
        if (!busy && status != null) {
            boolean paired = getSharedPreferences("phnlnk_audio_bootstrap", MODE_PRIVATE)
                    .getBoolean("adb_paired", false);
            if (paired) status.setText("Android pairing saved. Press Start local audio test helper.");
        }
    }

    private interface Work {String run() throws Exception;}

    private void runTask(String title, Work task) {
        if (busy) return;
        busy = true;
        updateStatus(title + "...");
        setEnabled(false);
        worker.execute(() -> {
            String result;
            try {
                result = task.run();
            } catch (Exception error) {
                result = "TEST FAILED: " + safe(error.getMessage());
            }
            final String done = result;
            main.post(() -> {
                busy = false;
                updateStatus(done);
                setEnabled(true);
            });
        });
    }

    private void updateStatus(String message) {
        if (status != null) status.setText(message);
    }

    private void setEnabled(boolean enabled) {
        if (capture != null) capture.setEnabled(enabled);
        if (inject != null) inject.setEnabled(enabled);
        if (launch != null) launch.setEnabled(enabled);
    }

    private TextView text(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setPadding(0,dp(8),0,dp(8));
        return text;
    }

    private Button button(String title, Runnable handler) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setOnClickListener(v->handler.run());
        return button;
    }

    private static String safe(String error) {
        if (error == null) return "Unknown error";
        return error.length() <= 200 ? error : error.substring(0,200);
    }

    private int dp(int value) {
        return Math.round(value*getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
