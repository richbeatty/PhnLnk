package com.richbeatty.phnlnk;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Uri deepLink = getIntent() == null ? null : getIntent().getData();
        if (getIntent() != null &&
                Intent.ACTION_DIAL.equals(getIntent().getAction())) {
            Intent hostDialer = new Intent(this, HostCallActivity.class);
            hostDialer.setData(deepLink);
            startActivity(hostDialer);
            finish();
            return;
        }
        if (deepLink != null && "phnlnk".equals(deepLink.getScheme())
                && "pair".equals(deepLink.getHost())) {
            getSharedPreferences("mode", MODE_PRIVATE).edit().putString("role","client").apply();
            Intent client = new Intent(this, ClientActivity.class);
            client.setData(deepLink);
            startActivity(client);
            finish();
            return;
        }
        SharedPreferences preferences = getSharedPreferences("mode", MODE_PRIVATE);
        String role = preferences.getString("role", "");
        if ("host".equals(role)) {
            startActivity(new Intent(this, HostActivity.class));
            finish();
            return;
        }
        if ("client".equals(role)) {
            startActivity(new Intent(this, ClientActivity.class));
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int)(22 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);
        TextView title = new TextView(this);
        title.setText("PhnLnk");
        title.setTextSize(32);
        root.addView(title);
        TextView intro = new TextView(this);
        intro.setText("Your number. More devices.\n\nWhat do you want this device to do?");
        intro.setTextSize(17);
        intro.setPadding(0, p, 0, p);
        root.addView(intro);
        root.addView(choice("This phone has my SIM", "host", HostActivity.class));
        root.addView(choice("Connect to my other phone", "client", ClientActivity.class));
        TextView note = new TextView(this);
        note.setText("\nPhone control preview: two-way call audio is not available on companion devices.");
        note.setTextSize(13);
        root.addView(note);
        setContentView(root);
    }

    private View choice(String label, String role, Class<?> activity) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(17);
        button.setOnClickListener(v -> {
            getSharedPreferences("mode", MODE_PRIVATE).edit()
                    .putString("role", role)
                    .putBoolean("hosting_enabled", "host".equals(role))
                    .apply();
            startActivity(new Intent(this, activity));
            finish();
        });
        return button;
    }
}
