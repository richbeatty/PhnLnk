package com.richbeatty.phnlnk;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Guided, OPTIONAL setup. Android must still authorize wireless ADB and
 * the user must enter the Android pairing code. This is an audio feasibility
 * probe, NOT the completed two-way cellular audio feature.
 */
public final class HostAudioActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION = 481;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView connection, details, testResult;
    private Button begin, retry, stop, capture, inject;
    private boolean busy;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshState();
            main.postDelayed(this, 1700);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        PhoneUi.screen(this);
        ScrollView scroller = new ScrollView(this);
        scroller.setBackgroundColor(PhoneUi.BG);
        LinearLayout root = PhoneUi.column(this);
        root.setPadding(dp(19),dp(24),dp(19),dp(24));
        scroller.addView(root);
        setContentView(scroller);

        root.addView(PhoneUi.label(this,"Audio access",28,PhoneUi.INK,true));
        root.addView(PhoneUi.label(this,
                "A guided compatibility test for your existing phone. No computer, extra hardware, root, or Shizuku.",
                14,PhoneUi.MUTED,false),PhoneUi.margins(this,7,14));

        LinearLayout statusCard = PhoneUi.card(this);
        statusCard.addView(PhoneUi.label(this,"CONNECTION STATUS",11,PhoneUi.MUTED,true));
        connection = PhoneUi.label(this,"Checking...",19,PhoneUi.INK,true);
        details = PhoneUi.label(this,"",14,PhoneUi.MUTED,false);
        statusCard.addView(connection,PhoneUi.margins(this,9,4));
        statusCard.addView(details);
        root.addView(statusCard);

        LinearLayout setup = PhoneUi.card(this);
        setup.addView(PhoneUi.label(this,"Set up once",23,PhoneUi.INK,true));
        setup.addView(PhoneUi.label(this,
                "1. Enable Developer options and Wireless Debugging in Android Settings.",
                14,PhoneUi.INK,false),PhoneUi.margins(this,12,9));
        setup.addView(PhoneUi.label(this,
                "2. In Wireless Debugging, select \"Pair device with pairing code\".",
                14,PhoneUi.INK,false),PhoneUi.margins(this,0,9));
        setup.addView(PhoneUi.label(this,
                "3. Leave the pairing dialog open. Swipe down your notifications and enter its six digits in the PhnLnk notification. Android approves the pairing.",
                14,PhoneUi.INK,false),PhoneUi.margins(this,0,15));
        setup.addView(PhoneUi.label(this,
                "PhnLnk remembers the paired identity and retries when Android permits it. If Wireless Debugging turns off, only Android Settings can turn it back on.",
                13,PhoneUi.MUTED,false),PhoneUi.margins(this,0,15));

        begin = mainButton("Start guided setup", this::requestGuidedSetup);
        setup.addView(begin);
        retry = secondaryButton("Retry saved connection", this::retryConnection);
        setup.addView(retry,PhoneUi.margins(this,10,0));
        stop = secondaryButton("Turn off automatic audio access", this::confirmDisable);
        setup.addView(stop,PhoneUi.margins(this,10,0));
        setup.addView(secondaryButton("Open Android Developer options", this::openSettings),
                PhoneUi.margins(this,10,0));
        setup.addView(secondaryButton("Pair again if Android forgot this app", this::requestGuidedSetup),
                PhoneUi.margins(this,10,0));
        setup.addView(PhoneUi.label(this,"CONNECTION PORT (ONLY IF AUTO-DISCOVERY FAILS)",
                11,PhoneUi.MUTED,true),PhoneUi.margins(this,17,7));
        setup.addView(PhoneUi.label(this,
                "Use the port beside the IP address on the main Wireless Debugging screen. "
                + "This is NOT the port in the six-digit pairing-code dialog.",
                12,PhoneUi.MUTED,false),PhoneUi.margins(this,0,7));
        final EditText portField = new EditText(this);
        portField.setSingleLine(true);
        portField.setInputType(InputType.TYPE_CLASS_NUMBER);
        portField.setTextSize(18);
        portField.setHint("Connection port, for example 38451");
        portField.setText(AudioAccessState.manualPort(this) > 0
                ? String.valueOf(AudioAccessState.manualPort(this)) : "");
        portField.setBackground(PhoneUi.background(this,PhoneUi.BG,13,PhoneUi.BORDER));
        portField.setPadding(dp(13),dp(12),dp(13),dp(12));
        setup.addView(portField,PhoneUi.margins(this,0,10));
        setup.addView(secondaryButton("Save port and retry",()->{
            String input = portField.getText().toString().trim();
            try {
                int port = input.isEmpty() ? 0 : Integer.parseInt(input);
                AudioAccessState.setManualPort(this,port);
                details.setText(port == 0 ? "Manual port cleared. Using automatic discovery."
                        : "Connection port saved. Retrying Android...");
                retryConnection();
            } catch (Exception error) {
                details.setText("Enter the port from Wireless Debugging (1024 through 65535).");
            }
        }));
        root.addView(setup,PhoneUi.margins(this,13,0));

        LinearLayout test = PhoneUi.card(this);
        test.addView(PhoneUi.label(this,"Test cellular audio",22,PhoneUi.INK,true));
        test.addView(PhoneUi.label(this,
                "With a consenting person on an active call, test the available Android call-audio routes. Audio is never saved. Then try the encrypted companion voice connection.",
                14,PhoneUi.MUTED,false),PhoneUi.margins(this,10,15));
        final Button captureMethod=secondaryButton(
                "Incoming capture: "+AudioAccessState.captureMethod(this),()->{});
        captureMethod.setOnClickListener(v->{
            captureMethod.setText("Incoming capture: "+AudioAccessState.cycleCapture(this));
            testResult.setText("Capture route saved for the next voice connection.");
        });
        test.addView(captureMethod,PhoneUi.margins(this,0,8));
        final Button uplinkMethod=secondaryButton(
                "Outgoing audio: "+AudioAccessState.uplinkMethod(this),()->{});
        uplinkMethod.setOnClickListener(v->{
            uplinkMethod.setText("Outgoing audio: "+AudioAccessState.cycleUplink(this));
            testResult.setText("Uplink route saved for the next voice connection.");
        });
        test.addView(uplinkMethod,PhoneUi.margins(this,0,12));
        capture = secondaryButton("Check incoming call sound", () ->
                new AlertDialog.Builder(this)
                        .setTitle("Measure call audio?")
                        .setMessage("This checks each available cellular audio source while the other person is talking. No audio is recorded or saved. Only proceed with the caller's permission.")
                        .setNegativeButton("Cancel",null)
                        .setPositiveButton("Test",(dialog,which)->
                                runTest("CAPTURE","Measuring call audio..."))
                        .show());
        test.addView(capture);
        inject = secondaryButton("Send quiet audio test tone", () ->
                new AlertDialog.Builder(this)
                        .setTitle("Play a test tone into the call?")
                        .setMessage("First tell the person on the call that you are sending a quiet half-second beep. Their confirmation is required to prove that it reached the caller.")
                        .setNegativeButton("Cancel",null)
                        .setPositiveButton("Play tone",(dialog,which)->
                                runTest("AUTO".equals(AudioAccessState.uplinkMethod(this)) ?
                                        "INJECT" : "INJECT:"+AudioAccessState.uplinkMethod(this),
                                        "Sending test tone..."))
                        .show());
        test.addView(inject,PhoneUi.margins(this,9,0));
        testResult = PhoneUi.label(this,"No audio test has been run.",14,PhoneUi.INK,false);
        testResult.setTextIsSelectable(true);
        test.addView(testResult,PhoneUi.margins(this,13,0));
        root.addView(test,PhoneUi.margins(this,13,0));

        root.addView(PhoneUi.label(this,
                "The encrypted companion voice transport is implemented, but Android may still return silence or route outgoing audio incorrectly. Only a live two-way test with another person can verify this firmware. Use the normal Phone app for emergency calls.",
                13,PhoneUi.MUTED,false),PhoneUi.margins(this,14,0));
        refreshState();
    }

    private void requestGuidedSetup() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION);
            return;
        }
        beginGuidedSetup();
    }

    private void beginGuidedSetup() {
        AudioAccessState.enable(this);
        AudioAccessState.set(this,AudioAccessState.PAIRING,
                "Waiting for Android's six-digit code in the PhnLnk notification.");
        try {
            startForegroundService(new Intent(this,AudioPairService.class)
                    .setAction(AudioPairService.START));
            main.postDelayed(this::openSettings,350);
        } catch (Exception error) {
            AudioAccessState.set(this,AudioAccessState.ERROR,
                    "Couldn't start the pairing notification: " + safe(error.getMessage()));
            refreshState();
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception error) {
            details.setText("Open Android Settings > System > Developer options manually.");
        }
    }

    private void retryConnection() {
        if (!AudioAccessState.isPaired(this)) {
            details.setText("First complete the one-time pairing through Android settings.");
            return;
        }
        AudioAccessState.enable(this);
        AudioAccessState.set(this,AudioAccessState.CONNECTING,"Retrying saved Android pairing...");
        try {
            startForegroundService(new Intent(this,AudioAccessService.class)
                    .setAction(AudioAccessService.ACTION_RETRY));
        } catch (Exception error) {
            AudioAccessState.set(this,AudioAccessState.ERROR,
                    "Android blocked the reconnect service. Reopen PhnLnk and try again.");
        }
        refreshState();
    }

    private void confirmDisable() {
        new AlertDialog.Builder(this)
                .setTitle("Turn off automatic reconnect?")
                .setMessage("PhnLnk will stop its helper and automatic reconnect attempts. Android's stored debugging authorization remains until you revoke it in Developer options > Wireless debugging > Paired devices.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Turn off",(dialog,which)->{
                    AudioAccessState.disable(this);
                    stopService(new Intent(this,AudioAccessService.class));
                    worker.execute(() -> {
                        try {AudioProbeClient.request(this,"STOP");}
                        catch (Exception ignored) { }
                    });
                    refreshState();
                }).show();
    }

    private void runTest(String command,String message) {
        if (busy || !AudioAccessState.READY.equals(AudioAccessState.state(this))) {
            testResult.setText("First connect the local audio helper.");
            return;
        }
        busy = true;
        capture.setEnabled(false);
        inject.setEnabled(false);
        testResult.setText(message);
        worker.execute(() -> {
            String result;
            try { result = AudioProbeClient.request(this,command); }
            catch (Exception e) {result = "Test failed: " + safe(e.getMessage());}
            final String done = result;
            main.post(() -> {
                busy = false;
                testResult.setText(done);
                refreshState();
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (AudioAccessState.autoReconnect(this) && AudioAccessState.isPaired(this)) {
            // A foreground activity can restart the opt-in service after
            // Android has killed it. No repeated pairing code is requested.
            try {
                startForegroundService(new Intent(this,AudioAccessService.class)
                        .setAction(AudioAccessService.ACTION_RETRY));
            } catch (Exception ignored) { }
        }
        main.removeCallbacks(refresh);
        main.post(refresh);
    }

    @Override protected void onPause() {
        main.removeCallbacks(refresh);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int request,
            String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if (request != NOTIFICATION_PERMISSION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            beginGuidedSetup();
        else details.setText("Notification permission is required so you can enter the pairing code while Android Settings stays open.");
    }

    private void refreshState() {
        if (connection == null) return;
        connection.setText(AudioAccessState.display(this));
        connection.setTextColor(AudioAccessState.READY.equals(AudioAccessState.state(this))
                ? PhoneUi.FOREST : PhoneUi.INK);
        details.setText(AudioAccessState.message(this));
        boolean paired = AudioAccessState.isPaired(this);
        boolean enabled = AudioAccessState.autoReconnect(this);
        begin.setVisibility(paired ? android.view.View.GONE : android.view.View.VISIBLE);
        retry.setVisibility(paired ? android.view.View.VISIBLE : android.view.View.GONE);
        stop.setVisibility(enabled ? android.view.View.VISIBLE : android.view.View.GONE);
        boolean ready = enabled && AudioAccessState.READY.equals(AudioAccessState.state(this));
        if (!busy) {
            capture.setEnabled(ready);
            inject.setEnabled(ready);
        }
    }

    private Button mainButton(String title,Runnable action) {
        Button button = PhoneUi.button(this,title,PhoneUi.CARD,PhoneUi.FOREST,Color.TRANSPARENT);
        button.setOnClickListener(v->action.run());
        return button;
    }

    private Button secondaryButton(String title,Runnable action) {
        Button button = PhoneUi.button(this,title,PhoneUi.FOREST,PhoneUi.PALE,Color.TRANSPARENT);
        button.setOnClickListener(v->action.run());
        return button;
    }

    private String safe(String error) {
        if (error == null) return "Unknown error";
        return error.length() > 180 ? error.substring(0,180) : error;
    }

    private int dp(int value) {
        return PhoneUi.dp(this,value);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
