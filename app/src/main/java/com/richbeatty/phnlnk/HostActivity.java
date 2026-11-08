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
        PhoneUi.screen(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PhoneUi.BG);
        LinearLayout root = PhoneUi.column(this);
        int margin = dp(19);
        root.setPadding(margin, dp(22), margin, dp(24));
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout title=PhoneUi.row(this);
        TextView mark=PhoneUi.label(this,"P",24,PhoneUi.CARD,true);
        mark.setGravity(android.view.Gravity.CENTER);
        mark.setBackground(PhoneUi.background(this,PhoneUi.FOREST,14,Color.TRANSPARENT));
        title.addView(mark,new LinearLayout.LayoutParams(dp(50),dp(50)));
        LinearLayout heading=PhoneUi.column(this);
        heading.addView(PhoneUi.label(this,"PhnLnk",27,PhoneUi.INK,true));
        heading.addView(PhoneUi.label(this,"MAIN PHONE",11,PhoneUi.MUTED,true));
        LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(0,-2,1f);
        headingParams.setMargins(dp(13),0,0,0);
        title.addView(heading,headingParams);
        root.addView(title);
        root.addView(PhoneUi.label(this,
                "Your number stays here. Other devices connect to it.",
                14,PhoneUi.MUTED,false),PhoneUi.margins(this,8,16));

        LinearLayout statusCard=PhoneUi.card(this);
        statusCard.addView(PhoneUi.label(this,"PHONE STATUS",11,PhoneUi.MUTED,true));
        status=PhoneUi.label(this,"Checking connection...",15,PhoneUi.INK,true);
        status.setTextIsSelectable(true);
        statusCard.addView(status,PhoneUi.margins(this,9,10));
        permissionsButton=button("Allow phone permissions",this::requestPhonePermissions);
        statusCard.addView(permissionsButton);
        root.addView(statusCard);

        LinearLayout pairCard=PhoneUi.card(this);
        pairCard.addView(PhoneUi.label(this,"Connect another device",23,PhoneUi.INK,true));
        pairCard.addView(PhoneUi.label(this,
                "Pair once with a QR code or six digits. The connection is remembered until you disconnect.",
                14,PhoneUi.MUTED,false),PhoneUi.margins(this,7,15));
        pairCard.addView(button("Pair a device", () -> {
            getSharedPreferences("mode", MODE_PRIVATE).edit().putBoolean("hosting_enabled",true).apply();
            if (!hasRequiredPermissions()) {
                requestPhonePermissions();
                return;
            }
            try {
                startForegroundService(new Intent(this,GatewayService.class));
                PairingManager.get(this).begin();
                updateStatus();
            } catch (Exception error) {
                status.setText("Unable to pair: " + error.getMessage());
            }
        }));
        pairInfo=PhoneUi.label(this,"Tap Pair a device to show a one-time code.",13,PhoneUi.MUTED,false);
        pairCard.addView(pairInfo,PhoneUi.margins(this,12,7));
        pairCode=PhoneUi.label(this,"",34,PhoneUi.FOREST,true);
        pairCode.setTextIsSelectable(true);
        pairCard.addView(pairCode);
        qr=new ImageView(this);
        qr.setAdjustViewBounds(true);
        pairCard.addView(qr,new LinearLayout.LayoutParams(dp(190),dp(190)));
        qr.setVisibility(View.GONE);
        pairCard.addView(secondary("Copy browser address",()->{
            String url=preferredUrl();
            if(url==null)return;
            ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("PhnLnk connection",url));
        }));
        root.addView(pairCard,PhoneUi.margins(this,13,0));

        LinearLayout settings=PhoneUi.card(this);
        settings.addView(PhoneUi.label(this,"Manage connection",19,PhoneUi.INK,true));
        settings.addView(secondary("Resume connection",()->{
            getSharedPreferences("mode", MODE_PRIVATE).edit().putBoolean("hosting_enabled",true).apply();
            try{startForegroundService(new Intent(this,GatewayService.class));}
            catch(Exception error){status.setText("Could not start: "+error.getMessage());}
            handler.postDelayed(this::updateStatus,450);
        }),PhoneUi.margins(this,13,9));
        settings.addView(secondary("Stop connection",()->{
            getSharedPreferences("mode", MODE_PRIVATE).edit().putBoolean("hosting_enabled",false).apply();
            stopService(new Intent(this,GatewayService.class));
            handler.postDelayed(this::updateStatus,300);
        }),PhoneUi.margins(this,0,9));
        settings.addView(secondary("Forget all paired devices",()->new AlertDialog.Builder(this)
                .setTitle("Disconnect every device?")
                .setMessage("They will need a new pairing code to connect again.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Disconnect",(dialog,which)->{
                    PairingManager.get(this).forgetAll();
                    updateStatus();
                }).show()),PhoneUi.margins(this,0,9));
        settings.addView(secondary("Use this as a companion instead",()->{
            stopService(new Intent(this,GatewayService.class));
            getSharedPreferences("mode",MODE_PRIVATE).edit()
                    .remove("role").putBoolean("hosting_enabled",false).apply();
            startActivity(new Intent(this,MainActivity.class));
            finish();
        }));
        root.addView(settings,PhoneUi.margins(this,13,0));

        root.addView(PhoneUi.label(this,
                "Calls can be controlled from another device on trusted private Wi-Fi. Audio still stays on this phone. Keep your normal Phone app for emergency calls.",
                12,PhoneUi.MUTED,false),PhoneUi.margins(this,13,0));
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

    private TextView text(String value,int sp) {
        TextView view=PhoneUi.label(this,value,sp,PhoneUi.INK,false);
        view.setPadding(0,dp(8),0,dp(8));
        return view;
    }

    private Button button(String label,Runnable action) {
        Button button=PhoneUi.button(this,label,PhoneUi.CARD,PhoneUi.FOREST,Color.TRANSPARENT);
        button.setOnClickListener(v->action.run());
        return button;
    }

    private Button secondary(String label,Runnable action) {
        Button button=PhoneUi.button(this,label,PhoneUi.FOREST,PhoneUi.PALE,Color.TRANSPARENT);
        button.setOnClickListener(v->action.run());
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
            status.setText("Host: " + (GatewayService.isRunning() ? "Ready" : "Off")
                    + "    Call: " + report.getString("callState")
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
