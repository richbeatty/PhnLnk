package com.richbeatty.phnlnk;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.text.InputType;
import android.content.Context;

/**
 * Real, user-opened host call UI. No showWhenLocked, turnScreenOn,
 * full-screen notification, or wake lock. Notification tap opens this.
 */
public final class HostCallActivity extends Activity {
    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable update = new Runnable() {
        @Override public void run() {
            if (status != null) {
                status.setText(PhoneInCallService.isRinging()
                        ? "Incoming call" : PhoneInCallService.hasCall()
                        ? "Call in progress" : "No active call");
            }
            ui.postDelayed(this, 600);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        root.setPadding(p,p,p,p);
        TextView heading = text("PhnLnk phone controls",26);
        root.addView(heading);
        status=text("Connecting...",17);
        root.addView(status);
        LinearLayout actions = line();
        actions.addView(button("Answer", () -> PhoneInCallService.answerCall()), weighted());
        actions.addView(button("End call", () -> PhoneInCallService.endCall()), weighted());
        root.addView(actions);
        root.addView(text("Dial a number", 18));
        EditText number = new EditText(this);
        number.setSingleLine(true);
        number.setInputType(InputType.TYPE_CLASS_PHONE);
        number.setHint("Phone number");
        if (getIntent() != null && getIntent().getData() != null
                && "tel".equals(getIntent().getData().getScheme())) {
            number.setText(getIntent().getData().getSchemeSpecificPart());
        }
        root.addView(number);
        root.addView(button("Call", () -> {
            try {
                CallController.dial(this, number.getText().toString());
                status.setText("Call requested.");
            } catch (Exception error) { status.setText(error.getMessage()); }
        }));
        root.addView(text("In-call keypad",19));
        String[][] pad={{"1","2","3"},{"4","5","6"},{"7","8","9"},{"*","0","#"}};
        for(String[] row : pad) {
            LinearLayout cells=line();
            for(String key : row) {
                Button item=button(key,()->{
                    try {PhoneInCallService.sendDtmf(key.charAt(0));}
                    catch(Exception error) {status.setText(error.getMessage());}
                });
                item.setTextSize(23);
                cells.addView(item, weighted());
            }
            root.addView(cells);
        }
        root.addView(text("No wake-on-call behavior is requested by this app. Android may still wake the screen through its own calling system.",12));
        setContentView(root);
    }

    @Override public void onResume() {
        super.onResume();
        ui.post(update);
    }
    @Override public void onPause() {
        ui.removeCallbacks(update);
        super.onPause();
    }
    private LinearLayout line() {
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }
    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0,-2,1f);
    }
    private TextView text(String value,int size) {
        TextView v=new TextView(this);
        v.setTextSize(size);
        v.setText(value);
        v.setPadding(0,dp(10),0,dp(10));
        return v;
    }
    private Button button(String label,Runnable f) {
        Button b=new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v->f.run());
        return b;
    }
    private int dp(int n) {
        return Math.round(n*getResources().getDisplayMetrics().density);
    }
}
