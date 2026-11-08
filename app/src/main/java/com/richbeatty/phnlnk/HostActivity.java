package com.richbeatty.phnlnk;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
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

public final class HostActivity extends Activity {
    private TextView status;
    private Button permissionsButton;
    private TextView pairCode;
    private TextView pairInfo;
    private ImageView qr;
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
        root.addView(text("Keep your number. Connect other devices.", 15));
        root.addView(text("Call control only. Audio stays on this phone.", 13));

        status = text("", 15);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(17), 0, dp(14));
        root.addView(status, statusParams);

        permissionsButton = button("Allow phone permissions", this::requestPhonePermissions);
        root.addView(permissionsButton);

        root.addView(text("Calling", 22));
        root.addView(text("Place and control calls using your existing phone number. PhnLnk does not need to replace your normal Phone app.", 13));
        root.addView(text("Audio on a second device is not supported by this build. Android restricts cellular call audio, and PhnLnk will not ask you to enable debugging or become the default Phone app.", 13));
        root.addView(button("Resume host connection", () -> {
            getSharedPreferences("mode", MODE_PRIVATE).edit().putBoolean("hosting_enabled", true).apply();
            try { startForegroundService(new Intent(this, GatewayService.class)); }
            catch (Exception error) { status.setText("Could not start: " + error.getMessage()); }
            handler.postDelayed(this::updateStatus, 500);
        }));
        root.addView(text("Pair another device", 22));
        root.addView(text("Scan the QR with the PhnLnk Android app, or enter six digits in your PC browser.", 15));
        root.addView(button("Pair a device", () -> {
            getSharedPreferences("mode", MODE_PRIVATE).edit().putBoolean("hosting_enabled",true).apply();
            if (!hasRequiredPermissions()) {
                requestPhonePermissions();
                return;
            }
            try {
                startForegroundService(new Intent(this, GatewayService.class));
                PairingManager.get(this).begin();
                updateStatus();
            } catch (Exception error) {
                status.setText("Unable to start pairing: " + error.getMessage());
            }
        }));
        pairInfo = text("Tap Pair a device when you're ready.", 14);
        root.addView(pairInfo);
        pairCode = text("", 34);
        pairCode.setTextIsSelectable(true);
        root.addView(pairCode);
        qr = new ImageView(this);
        qr.setAdjustViewBounds(true);
        root.addView(qr, new LinearLayout.LayoutParams(dp(185), dp(185)));
        qr.setVisibility(View.GONE);
        root.addView(button("Copy connection address", () -> {
            String url = preferredUrl();
            if (url == null) return;
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("PhnLnk address", url));
        }));
        root.addView(button("Forget all paired devices", () ->
                new AlertDialog.Builder(this)
                        .setTitle("Disconnect all devices?")
                        .setMessage("Connected tablets and computers will need a new code.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Disconnect", (dialog, which) -> {
                            PairingManager.get(this).forgetAll();
                            updateStatus();
                        }).show()));

        root.addView(button("Stop host", () -> {
            getSharedPreferences("mode", MODE_PRIVATE).edit().putBoolean("hosting_enabled", false).apply();
            stopService(new Intent(this, GatewayService.class));
            handler.postDelayed(this::updateStatus, 300);
        }));

        root.addView(button("Use this device as a companion instead", () -> {
            stopService(new Intent(this, GatewayService.class));
            getSharedPreferences("mode", MODE_PRIVATE).edit().remove("role").apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }));
        root.addView(text("Important", 22));
        root.addView(text(
                "Calls are controlled here. The remote device cannot hear or speak yet. " +
                "Use only on trusted home Wi-Fi. Do not expose the host port to the internet. " +
                "Use your regular phone app for emergencies.", 13));
        if (hasRequiredPermissions()) {
            if (getSharedPreferences("mode", MODE_PRIVATE).getBoolean("hosting_enabled", true)) {
                try { startForegroundService(new Intent(this, GatewayService.class)); }
                catch (Exception ignored) { }
            }
        } else {
            requestPhonePermissions();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 9 && hasRequiredPermissions()) {
            try { startForegroundService(new Intent(this, GatewayService.class)); }
            catch (Exception ignored) { }
        }
        updateStatus();
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
            if (permissionsButton != null) permissionsButton.setVisibility(
                    hasRequiredPermissions() ? View.GONE : View.VISIBLE);
            String link = preferredUrl();
            RoleManager role = (RoleManager) getSystemService(ROLE_SERVICE);
            boolean advanced = role != null && role.isRoleHeld(RoleManager.ROLE_DIALER);
            status.setText("Host: " + (GatewayService.isRunning() ? "Ready" : "Off")
                    + "    Call: " + report.getString("callState")
                    + "\nIn-call tones: " + (advanced ? "Enabled" : "Set PhnLnk as default Phone app")
                    + "\nConnected devices: " + PairingManager.get(this).count()
                    + "\n\nFor a PC browser: "
                    + (link == null ? "Connect this phone to Wi-Fi" : link));
            String code = PairingManager.get(this).current();
            String url = preferredUrl();
            if (code == null || url == null) {
                pairCode.setText("");
                pairInfo.setText("Tap Pair a device to generate a new code.");
                qr.setVisibility(View.GONE);
                qr.setTag(null);
            } else {
                pairInfo.setText("Scan with PhnLnk on another Android device, or enter this code on your PC. Expires in 5 minutes:");
                pairCode.setText(code);
                qr.setVisibility(View.VISIBLE);
                String ip = url.substring("http://".length(), url.lastIndexOf(':'));
                String qrValue = "phnlnk://pair?host=" + ip + "&code=" + code;
                if (!qrValue.equals(qr.getTag())) {
                    qr.setImageBitmap(renderQr(qrValue));
                    qr.setTag(qrValue);
                }
            }
        } catch (Exception exception) {
            status.setText("Status unavailable: " + exception.getMessage());
        }
    }

    private String preferredUrl() {
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            String fallback = null;
            while (all.hasMoreElements()) {
                NetworkInterface device = all.nextElement();
                if (!device.isUp() || device.isLoopback()) continue;
                Enumeration<InetAddress> addresses = device.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress ip = addresses.nextElement();
                    if (!(ip instanceof Inet4Address) || ip.isLinkLocalAddress() || ip.isLoopbackAddress()) continue;
                    String link = "http://" + ip.getHostAddress() + ":" + Config.PORT;
                    if (device.getName().startsWith("wlan")) return link;
                    if (ip.isSiteLocalAddress()) fallback = link;
                }
            }
            return fallback;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap renderQr(String url) throws Exception {
        BitMatrix matrix = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 240, 240);
        Bitmap bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 240; y++) {
            for (int x = 0; x < 240; x++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }

}
