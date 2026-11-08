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
        PhoneUi.screen(this);
        LinearLayout root=PhoneUi.column(this);
        root.setBackgroundColor(PhoneUi.BG);
        int pad=PhoneUi.dp(this,23);
        root.setPadding(pad,PhoneUi.dp(this,42),pad,pad);
        TextView mark=PhoneUi.label(this,"P",29,PhoneUi.CARD,true);
        mark.setGravity(android.view.Gravity.CENTER);
        mark.setBackground(PhoneUi.background(this,PhoneUi.FOREST,22,android.graphics.Color.TRANSPARENT));
        root.addView(mark,new LinearLayout.LayoutParams(PhoneUi.dp(this,76),PhoneUi.dp(this,76)));
        root.addView(PhoneUi.label(this,"PhnLnk",36,PhoneUi.INK,true),PhoneUi.margins(this,24,5));
        root.addView(PhoneUi.label(this,"Your phone. More places.",17,PhoneUi.MUTED,false));
        PhoneUi.space(root,this,32);
        LinearLayout panel=PhoneUi.card(this);
        panel.addView(PhoneUi.label(this,"How will you use this device?",20,PhoneUi.INK,true));
        panel.addView(PhoneUi.label(this,"You can change this later. Your SIM and phone number stay where they are.",13,PhoneUi.MUTED,false),PhoneUi.margins(this,8,18));
        panel.addView(choice("This phone has my SIM","host",HostActivity.class));
        panel.addView(choice("Connect to another phone","client",ClientActivity.class),PhoneUi.margins(this,10,0));
        root.addView(panel);
        root.addView(PhoneUi.label(this,
                "Call-control preview. Remote two-way cellular voice audio is not yet supported.",
                13,PhoneUi.MUTED,false),PhoneUi.margins(this,19,0));
        setContentView(root);
    }

    private View choice(String label,String role,Class<?> activity) {
        Button button=PhoneUi.button(this,label,PhoneUi.CARD,PhoneUi.FOREST,android.graphics.Color.TRANSPARENT);
        button.setOnClickListener(v->{
            getSharedPreferences("mode",MODE_PRIVATE).edit()
                    .putString("role",role)
                    .putBoolean("hosting_enabled","host".equals(role))
                    .apply();
            startActivity(new Intent(this,activity));
            finish();
        });
        return button;
    }
}
