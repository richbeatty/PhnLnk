package com.richbeatty.phnlnk;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private TextView status;
    private TextView key;
    private boolean keyShown;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            updateStatus();
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int margin = dp(22);
        root.setPadding(margin, margin, margin, margin);
        scroll.addView(root);
        setContentView(scroll);

        TextView heading = text("PhnLnk", 29);
        root.addView(heading);
        root.addView(text("Host for your AT&T line on the Minimal Phone MP01", 15));
        root.addView(text("Prototype 0.1: browser call control. Voice audio is NOT relayed yet.", 15));

        status = text("", 15);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(17), 0, dp(14));
        root.addView(status, statusParams);

        root.addView(button("Grant phone permissions", () -> requestPhonePermissions()));
        root.addView(button("Start host", () -> {
            if (!hasRequiredPermissions()) {
                requestPhonePermissions();
                return;
            }
            try {
                startForegroundService(new Intent(this, GatewayService.class));
            } catch (Exception e) {
                status.setText("Could not start: " + e.getMessage());
            }
            handler.postDelayed(this::updateStatus, 500);
        }));
        root.addView(button("Stop host", () -> {
            stopService(new Intent(this, GatewayService.class));
            handler.postDelayed(this::updateStatus, 300);
        }));

        root.addView(text("Pairing", 22));
        root.addView(text("On a tablet or PC, open the host URL. Sign in with username phnlnk and this pairing key:", 15));
        key = text("••••••••••••••••", 14);
        key.setTextIsSelectable(true);
        root.addView(key);
        root.addView(button("Show or hide pairing key", () -> {
            keyShown = !keyShown;
            key.setText(keyShown ? Config.pairingKey(this) : "••••••••••••••••");
        }));
        root.addView(button("Copy pairing key", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("PhnLnk pairing key", Config.pairingKey(this)));
        }));

        root.addView(text("Important", 22));
        root.addView(text(
                "This build controls the cellular dialer but does not relay speech. " +
                "Use only on a trusted home network or across a private VPN. Never forward the host port publicly. " +
                "For emergencies, use your phone directly, not this prototype.", 15));
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

    private TextView text(String value, int sp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setPadding(0, dp(9), 0, dp(9));
        return view;
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private boolean hasRequiredPermissions() {
        return checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPhonePermissions() {
        ArrayList<String> needed = new ArrayList<>();
        String[] phone = {
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ANSWER_PHONE_CALLS
        };
        for (String permission : phone) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) needed.add(permission);
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), 9);
        else updateStatus();
    }

    private void updateStatus() {
        try {
            JSONObject report = CallController.status(this);
            StringBuilder sb = new StringBuilder();
            sb.append("Host: ").append(GatewayService.isRunning() ? "ON" : "OFF");
            sb.append("\nCall: ").append(report.getString("callState"));
            sb.append("\nPhone permission: ").append(hasRequiredPermissions() ? "ready" : "not granted");
            sb.append("\nDevice: ").append(report.getString("model"));
            sb.append("\nAndroid: ").append(report.getString("android"));
            sb.append("\nCPU: ").append(report.getString("abi"));
            sb.append("\nTelephony audio device visible: ")
                    .append(report.getBoolean("telephonyDeviceVisible") ? "yes (not proof of bridging)" : "no");
            sb.append("\n\nOpen in browser (on same trusted network or private VPN):");
            sb.append(networkAddresses());
            status.setText(sb.toString());
        } catch (Exception exception) {
            status.setText("Status unavailable: " + exception.getMessage());
        }
    }

    private String networkAddresses() {
        StringBuilder addresses = new StringBuilder();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface net = interfaces.nextElement();
                if (!net.isUp() || net.isLoopback()) continue;
                Enumeration<InetAddress> ips = net.getInetAddresses();
                while (ips.hasMoreElements()) {
                    InetAddress address = ips.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLinkLocalAddress()
                            || address.isLoopbackAddress()) continue;
                    addresses.append("\nhttp://").append(address.getHostAddress())
                            .append(":").append(Config.PORT);
                }
            }
        } catch (Exception ignored) {
            // Some vendor ROMs do not expose all interfaces.
        }
        return addresses.length() == 0 ? "\nNo network address detected" : addresses.toString();
    }
}
