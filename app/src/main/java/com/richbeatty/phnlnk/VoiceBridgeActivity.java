package com.richbeatty.phnlnk;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class VoiceBridgeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            updateDisplay();
            handler.postDelayed(this, 1000);
        }
    };
    private LinearLayout screen;
    private TextView status;
    private EditText phone;
    private Button answerButton, hangupButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        screen.setPadding(pad,pad,pad,pad);
        scroll.addView(screen);
        setContentView(scroll);

        screen.addView(label("PhnLnk Voice", 29));
        screen.addView(label("Talk and listen from this Android device through a Bluetooth-to-SIP bridge.", 15));
        screen.addView(label("Requires a Linux Bluetooth hands-free gateway paired with your main phone. No root or Shizuku on either Android device.", 13));
        status = label("Voice bridge is off", 16);
        screen.addView(status);

        if (VoiceCredentials.configured(this)) {
            screen.addView(button("Start voice bridge", this::enableVoice));
            screen.addView(button("Stop voice bridge", this::disableVoice));
            screen.addView(button("Change gateway account", () -> showSetup(true)));
        } else {
            screen.addView(button("Set up Bluetooth voice bridge", () -> showSetup(false)));
        }

        screen.addView(label("Call using your existing number", 19));
        phone = new EditText(this);
        phone.setSingleLine(true);
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setTextSize(24);
        phone.setHint("Phone number");
        screen.addView(phone);

        String[][] keys = {
                {"1","2","3"}, {"4","5","6"}, {"7","8","9"}, {"*","0","#"}
        };
        for (String[] group : keys) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (String digit : group) {
                Button button = button(digit, () -> {
                    SipVoiceEngine engine = SipVoiceEngine.current();
                    if (engine != null && engine.isInCall()) {
                        try { engine.sendTone(digit.charAt(0)); }
                        catch (Exception e) { status.setText(e.getMessage()); }
                    } else phone.append(digit);
                });
                button.setTextSize(22);
                row.addView(button, new LinearLayout.LayoutParams(0,-2,1f));
            }
            screen.addView(row);
        }
        screen.addView(button("Delete last digit", () -> {
            if (phone.length()>0) phone.getText().delete(phone.length()-1,phone.length());
        }));
        screen.addView(button("Call over Bluetooth voice bridge", () -> {
            SipVoiceEngine engine = SipVoiceEngine.current();
            if (engine == null || !engine.isRegistered()) {
                status.setText("Start voice bridge and wait for registration first.");
                return;
            }
            try {engine.dial(phone.getText().toString());}
            catch (Exception e) {status.setText(e.getMessage());}
        }));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        answerButton = button("Answer here", () -> {
            try {SipVoiceEngine engine=SipVoiceEngine.current();
                if (engine != null) engine.answer();}
            catch (Exception e) {status.setText(e.getMessage());}
        });
        hangupButton = button("End voice call", () -> {
            try {SipVoiceEngine engine=SipVoiceEngine.current();
                if (engine != null) engine.hangup();}
            catch (Exception e) {status.setText(e.getMessage());}
        });
        actions.addView(answerButton, new LinearLayout.LayoutParams(0,-2,1f));
        actions.addView(hangupButton, new LinearLayout.LayoutParams(0,-2,1f));
        screen.addView(actions);
        screen.addView(label("During an active voice call, the keypad above sends menu tones.", 13));
        screen.addView(label("Beta warning: your Linux Bluetooth-to-SIP gateway must already be working. This screen cannot access cellular call audio by itself. Test on the same trusted Wi-Fi before remote use.", 13));
        updateDisplay();
    }

    private void updateDisplay() {
        if (status == null) return;
        SipVoiceEngine engine = SipVoiceEngine.current();
        boolean ready = engine != null && engine.isRegistered();
        status.setText(engine == null ?
                "Voice bridge offline. Set up your Bluetooth gateway and press Start." :
                engine.status() + "\n" + engine.callStatus());
        if (answerButton != null) answerButton.setEnabled(engine != null && engine.isIncoming());
        if (hangupButton != null) hangupButton.setEnabled(engine != null && engine.isInCall());
    }

    private void showSetup(boolean edit) {
        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.VERTICAL);
        inputs.setPadding(dp(16),dp(8),dp(16),dp(8));
        EditText host = new EditText(this);
        host.setSingleLine(true);
        host.setHint("Linux Bluetooth bridge IP");
        host.setInputType(InputType.TYPE_CLASS_TEXT);
        inputs.addView(host);
        EditText user = new EditText(this);
        user.setSingleLine(true);
        user.setHint("SIP extension (for example 1001)");
        inputs.addView(user);
        EditText pass = new EditText(this);
        pass.setSingleLine(true);
        pass.setHint("SIP password");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        inputs.addView(pass);

        new AlertDialog.Builder(this)
                .setTitle("Bluetooth voice bridge")
                .setMessage("Connect the phone to your Bluetooth-to-SIP gateway first. Use its private Wi-Fi IP and SIP account. Never use public SIP endpoints or default passwords.")
                .setView(inputs)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        VoiceCredentials.save(this, host.getText().toString(),
                                user.getText().toString(),pass.getText().toString());
                        disableVoice();
                        render();
                    } catch (Exception e) {
                        status.setText("Could not save: " + e.getMessage());
                    }
                }).show();
    }

    private void enableVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 456);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},457);
            return;
        }
        VoiceCredentials.setEnabled(this,true);
        try {
            startForegroundService(new Intent(this,SipVoiceService.class));
            status.setText("Connecting to the Bluetooth voice gateway...");
        } catch (Exception e) {
            VoiceCredentials.setEnabled(this,false);
            status.setText("Could not start voice bridge: " + e.getMessage());
        }
    }

    private void disableVoice() {
        VoiceCredentials.setEnabled(this,false);
        stopService(new Intent(this,SipVoiceService.class));
        if (status != null) status.setText("Voice bridge disconnected");
    }

    @Override public void onRequestPermissionsResult(int request, String[] p, int[] results) {
        super.onRequestPermissionsResult(request,p,results);
        if (request == 456 || request == 457) {
            boolean granted = results.length>0 && results[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) enableVoice();
            else status.setText("Voice calls need microphone and notification permissions.");
        }
    }

    private TextView label(String value,int size) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setPadding(0,dp(8),0,dp(8));
        return v;
    }

    private Button button(String title,Runnable handler) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setOnClickListener(v->handler.run());
        return b;
    }

    private int dp(int value) {
        return Math.round(value*getResources().getDisplayMetrics().density);
    }
}
