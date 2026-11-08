package com.richbeatty.phnlnk;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.database.Cursor;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.GridLayout;
import android.graphics.Typeface;
import android.text.InputType;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ClientActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<Contact> contacts = new ArrayList<>();
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (RemoteClient.isPaired(ClientActivity.this)) fetchStatus();
            ui.postDelayed(this, 3000);
        }
    };
    private LinearLayout root, tabContent;
    private TextView connection, note, callCaption;
    private LinearLayout callControls;
    private final ArrayList<Button> navigation = new ArrayList<>();
    private EditText number, search;
    private Button dial, answer, end, alerts, voice, muteVoice, voiceRoute;
    private TextView voiceStatus;
    private String tab = "Dial";
    private boolean contactsLoaded;
    private volatile boolean dtmfReady;
    private volatile String activeState = "unknown";
    private volatile boolean polling;

    static final class Contact {
        final String name;
        final String phone;
        Contact(String n, String p) { name = n; phone = p; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildShell();
        Uri link = getIntent() == null ? null : getIntent().getData();
        if (link != null && "phnlnk".equals(link.getScheme())
                && "pair".equals(link.getHost())) {
            String ip = link.getQueryParameter("host");
            String code = link.getQueryParameter("code");
            showPair(ip == null ? "" : ip, code == null ? "" : code);
            if (ip != null && code != null) connect(ip, code);
        } else if (RemoteClient.isPaired(this)) {
            showDialer();
        } else {
            showPair("", "");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        ui.removeCallbacks(poll);
        ui.post(poll);
        if (RemoteClient.isPaired(this)) startAlertsIfEnabled();
    }

    @Override protected void onPause() {
        ui.removeCallbacks(poll);
        super.onPause();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void buildShell() {
        PhoneUi.screen(this);
        ScrollView scrolling = new ScrollView(this);
        scrolling.setFillViewport(true);
        scrolling.setBackgroundColor(PhoneUi.BG);
        root = PhoneUi.column(this);
        int pad = dp(19);
        root.setPadding(pad, dp(22), pad, dp(25));
        scrolling.addView(root);
        setContentView(scrolling);

        LinearLayout header = PhoneUi.row(this);
        TextView mark = PhoneUi.label(this, "P", 24, PhoneUi.CARD, true);
        mark.setGravity(android.view.Gravity.CENTER);
        mark.setBackground(PhoneUi.background(this, PhoneUi.FOREST, 14, android.graphics.Color.TRANSPARENT));
        header.addView(mark,new LinearLayout.LayoutParams(dp(50),dp(50)));
        LinearLayout heading = PhoneUi.column(this);
        heading.addView(PhoneUi.label(this,"PhnLnk",27,PhoneUi.INK,true));
        heading.addView(PhoneUi.label(this,"COMPANION",11,PhoneUi.MUTED,true));
        LinearLayout.LayoutParams headingMargin=new LinearLayout.LayoutParams(0,-2,1f);
        headingMargin.setMargins(dp(13),0,0,0);
        header.addView(heading,headingMargin);
        root.addView(header);
        root.addView(PhoneUi.label(this,"Your phone, wherever you're sitting.",14,PhoneUi.MUTED,false),
                PhoneUi.margins(this,8,18));
    }

    private void clearScreen() {
        while (root.getChildCount() > 2) root.removeViewAt(2);
        connection = null;
        note = null;
        tabContent = null;
    }

    private void showPair(String ip, String code) {
        clearScreen();
        LinearLayout card = PhoneUi.card(this);
        card.addView(PhoneUi.label(this,"Connect a phone",25,PhoneUi.INK,true));
        card.addView(PhoneUi.label(this,"Scan the code on your main phone, or enter its Wi-Fi address and six-digit pairing code.",14,PhoneUi.MUTED,false),
                PhoneUi.margins(this,9,16));
        card.addView(PhoneUi.label(this,"PHONE ADDRESS",12,PhoneUi.MUTED,true));
        EditText address = input("192.168.1.10", InputType.TYPE_CLASS_TEXT);
        address.setText(ip);
        card.addView(address,PhoneUi.margins(this,6,16));
        card.addView(PhoneUi.label(this,"ONE-TIME CODE",12,PhoneUi.MUTED,true));
        EditText digits = input("000000", InputType.TYPE_CLASS_NUMBER);
        digits.setText(code);
        digits.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        card.addView(digits,PhoneUi.margins(this,6,17));
        card.addView(button("Connect phone", () ->
                connect(address.getText().toString(),digits.getText().toString())));
        note = PhoneUi.label(this,"",13,PhoneUi.RED,false);
        card.addView(note,PhoneUi.margins(this,12,0));
        root.addView(card);
        root.addView(PhoneUi.label(this,"Pair once. Your connection is remembered until you disconnect it. Both devices need a trusted private Wi-Fi connection or an encrypted private VPN. Do not expose the host port publicly.",13,PhoneUi.MUTED,false),
                PhoneUi.margins(this,15,8));
        root.addView(secondary("Use this device as the main phone",this::changeRole));
    }

    private void connect(String ip, String code) {
        if (note != null) note.setText("Connecting...");
        worker.execute(() -> {
            try {
                RemoteClient.pair(this, ip, code);
                runOnUiThread(() -> {
                    showDialer();
                    fetchStatus();
                    // Only request notification permission as part of explicit user setup.
                    prefs().edit().putBoolean("alerts", true).apply();
                    updateAlertsButton();
                    if (Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                                    != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 43);
                    } else startAlertsIfEnabled();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (note != null) note.setText(e.getMessage()); });
            }
        });
    }

    private void showDialer() {
        clearScreen();
        navigation.clear();

        LinearLayout statusCard = PhoneUi.card(this);
        statusCard.addView(PhoneUi.label(this,"PHONE CONNECTION",11,PhoneUi.MUTED,true));
        connection = PhoneUi.label(this,"Connecting...",17,PhoneUi.INK,true);
        statusCard.addView(connection,PhoneUi.margins(this,7,0));
        root.addView(statusCard,PhoneUi.margins(this,0,12));

        LinearLayout callCard = PhoneUi.card(this);
        callCard.setBackground(PhoneUi.background(this,PhoneUi.PALE,22,android.graphics.Color.TRANSPARENT));
        callCard.addView(PhoneUi.label(this,"CALL CONTROL",11,PhoneUi.FOREST,true));
        callCaption=PhoneUi.label(this,"Your main phone handles the audio.",14,PhoneUi.INK,false);
        callCard.addView(callCaption,PhoneUi.margins(this,7,12));
        callControls=PhoneUi.row(this);
        answer=button("Answer",()->command("answer",""));
        end=PhoneUi.button(this,"End call",PhoneUi.RED,PhoneUi.RED_PALE,PhoneUi.RED_PALE);
        end.setOnClickListener(v->command("end",""));
        callControls.addView(answer,PhoneUi.weight(this));
        callControls.addView(end,PhoneUi.weight(this));
        callControls.setVisibility(View.GONE);
        callCard.addView(callControls);
        LinearLayout audioControls=PhoneUi.column(this);
        voice=button("Start voice on this device",this::toggleVoice);
        audioControls.addView(voice,PhoneUi.margins(this,13,8));
        LinearLayout audioOptions=PhoneUi.row(this);
        muteVoice=secondary("Mute microphone",()->{
            VoiceSessionService.setMuted(!VoiceSessionService.muted());
            updateVoiceButtons();
        });
        voiceRoute=secondary("Use earpiece",()->{
            VoiceSessionService.setSpeaker(!VoiceSessionService.speaker());
            updateVoiceButtons();
        });
        audioOptions.addView(muteVoice,PhoneUi.weight(this));
        audioOptions.addView(voiceRoute,PhoneUi.weight(this));
        audioControls.addView(audioOptions);
        voiceStatus=PhoneUi.label(this,
                "Voice uses an encrypted private Wi-Fi connection. Cellular capture on the main phone requires its audio helper.",
                12,PhoneUi.INK,false);
        audioControls.addView(voiceStatus,PhoneUi.margins(this,10,0));
        callCard.addView(audioControls);
        root.addView(callCard,PhoneUi.margins(this,0,13));

        note=PhoneUi.label(this,"",13,PhoneUi.RED,false);
        root.addView(note,PhoneUi.margins(this,0,7));

        LinearLayout nav = PhoneUi.row(this);
        for(String item:new String[]{"Dial","Contacts","Favorites","Recents"}){
            Button button=PhoneUi.button(this,item,PhoneUi.MUTED,PhoneUi.CARD,PhoneUi.BORDER);
            button.setTextSize(12);
            button.setPadding(dp(4),dp(12),dp(4),dp(12));
            button.setOnClickListener(v->selectTab(item));
            navigation.add(button);
            nav.addView(button,PhoneUi.weight(this));
        }
        root.addView(nav,PhoneUi.margins(this,6,16));

        number=input("Phone number",InputType.TYPE_CLASS_PHONE);
        number.setTextSize(27);
        number.setTextColor(PhoneUi.INK);
        number.setGravity(android.view.Gravity.CENTER);
        number.setSingleLine(true);
        dial=button("Call using main phone",()->command("dial",number.getText().toString()));
        tabContent=PhoneUi.column(this);
        root.addView(tabContent);
        selectTab("Dial");

        PhoneUi.space(root,this,17);
        LinearLayout settingsPanel=PhoneUi.card(this);
        settingsPanel.setVisibility(View.GONE);
        settingsPanel.addView(PhoneUi.label(this,"Connection settings",19,PhoneUi.INK,true));
        settingsPanel.addView(PhoneUi.label(this,"Your saved pairing remains active across app restarts.",13,PhoneUi.MUTED,false),
                PhoneUi.margins(this,5,14));

        alerts=secondary("Incoming call alerts",this::toggleAlerts);
        settingsPanel.addView(alerts,PhoneUi.margins(this,0,9));
        settingsPanel.addView(secondary("Update main phone's private address", () -> {
            EditText field=input("Main phone Wi-Fi IP",InputType.TYPE_CLASS_TEXT);
            String current=RemoteClient.address(this);
            if(current.startsWith("http://")){
                String host=current.substring(7);
                if(host.endsWith(":47321")) host=host.substring(0,host.length()-6);
                field.setText(host);
            }
            LinearLayout wrap=PhoneUi.column(this);
            wrap.setPadding(dp(18),0,dp(18),0);
            wrap.addView(field);
            new AlertDialog.Builder(this)
                    .setTitle("Update phone address")
                    .setMessage("The phone's IP changed? You can reconnect without pairing again.")
                    .setView(wrap)
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Save",(dialog,which)->{
                        try {
                            RemoteClient.changeAddress(this,field.getText().toString());
                            if(note!=null) note.setText("Address saved. Pairing kept.");
                            fetchStatus();
                        }catch(Exception error){
                            if(note!=null) note.setText(error.getMessage());
                        }
                    }).show();
        }),PhoneUi.margins(this,0,9));
        settingsPanel.addView(secondary("Disconnect this device",()->{
            new AlertDialog.Builder(this)
                    .setTitle("Disconnect this device?")
                    .setMessage("You will need a fresh pairing code to reconnect.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Disconnect",(dialog,which)->{
                        stopService(new Intent(this,CompanionService.class));
                        stopService(new Intent(this,VoiceSessionService.class));
                        RemoteClient.forget(this);
                        showPair("","");
                    }).show();
        }),PhoneUi.margins(this,0,9));
        settingsPanel.addView(secondary("Switch to main phone mode",this::changeRole));
        Button toggle=secondary("Connection settings",()->settingsPanel.setVisibility(
                settingsPanel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        root.addView(toggle);
        root.addView(settingsPanel,PhoneUi.margins(this,10,0));
        updateAlertsButton();
        updateVoiceButtons();
        fetchStatus();
    }

    private void toggleVoice() {
        if(VoiceSessionService.active()) {
            stopService(new Intent(this,VoiceSessionService.class)
                    .setAction(VoiceSessionService.ACTION_STOP));
            if(voiceStatus!=null)voiceStatus.setText("Disconnecting voice...");
            return;
        }
        if(!"active".equals(activeState)){
            if(voiceStatus!=null)voiceStatus.setText(
                    "Place or answer a cellular call first, then start voice.");
            return;
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                !=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},47);
            return;
        }
        startVoice();
    }

    private void startVoice(){
        try {
            startForegroundService(new Intent(this,VoiceSessionService.class)
                    .setAction(VoiceSessionService.ACTION_START));
            if(voiceStatus!=null)voiceStatus.setText("Opening encrypted voice...");
        }catch(Exception error){
            if(voiceStatus!=null)voiceStatus.setText(
                    "Cannot start voice: "+error.getMessage());
        }
    }

    private void updateVoiceButtons(){
        if(voice==null)return;
        boolean connected=VoiceSessionService.active();
        voice.setText(connected?"Disconnect voice":"Start voice on this device");
        voice.setEnabled(connected || "active".equals(activeState));
        if(muteVoice!=null){
            muteVoice.setVisibility(connected?View.VISIBLE:View.GONE);
            muteVoice.setText(VoiceSessionService.muted()?
                    "Unmute microphone":"Mute microphone");
        }
        if(voiceRoute!=null){
            voiceRoute.setVisibility(connected?View.VISIBLE:View.GONE);
            voiceRoute.setText(VoiceSessionService.speaker()?
                    "Use earpiece":"Use speaker");
        }
        if(voiceStatus!=null && (connected || !"active".equals(activeState)))
            voiceStatus.setText(VoiceSessionService.state());
    }

    private void selectTab(String name) {
        tab=name;
        for(int i=0;i<navigation.size();i++){
            Button b=navigation.get(i);
            boolean selected=new String[]{"Dial","Contacts","Favorites","Recents"}[i].equals(name);
            b.setBackground(PhoneUi.background(this,
                    selected?PhoneUi.FOREST:PhoneUi.CARD,14,
                    selected?android.graphics.Color.TRANSPARENT:PhoneUi.BORDER));
            b.setTextColor(selected?PhoneUi.CARD:PhoneUi.MUTED);
        }
        if(tabContent==null) return;
        tabContent.removeAllViews();
        if("Dial".equals(name)) addKeypad(tabContent);
        else if("Contacts".equals(name)) showContacts();
        else if("Favorites".equals(name)) showFavorites();
        else showRecents();
    }

    private void addKeypad(LinearLayout target) {
        LinearLayout dialCard=PhoneUi.card(this);
        dialCard.addView(PhoneUi.label(this,"Enter a number",18,PhoneUi.INK,true));
        dialCard.addView(number,PhoneUi.margins(this,8,12));
        final String[][] keys={
                {"1","2","3"},{"4","5","6"},{"7","8","9"},{"*","0","#"}
        };
        for(String[] group:keys){
            LinearLayout line=PhoneUi.row(this);
            for(String digit:group){
                Button key=PhoneUi.button(this,digit,PhoneUi.INK,PhoneUi.BG,android.graphics.Color.TRANSPARENT);
                key.setTextSize(27);
                key.setMinHeight(dp(61));
                key.setMinimumHeight(dp(61));
                key.setBackground(PhoneUi.background(this,PhoneUi.BG,31,android.graphics.Color.TRANSPARENT));
                key.setOnClickListener(v->{
                    if("active".equals(activeState)){
                        if(dtmfReady) command("dtmf",digit);
                        else if(VoiceSessionService.active()){
                            VoiceSessionService.sendTone(digit.charAt(0));
                            if(note!=null)note.setText(
                                    "Sent in-band keypad tone. Menu detection depends on the cellular network.");
                        }else if(note!=null)note.setText(
                                "Start companion voice first to try in-band menu tones.");
                    }else number.append(digit);
                });
                line.addView(key,PhoneUi.weight(this));
            }
            dialCard.addView(line,PhoneUi.margins(this,2,6));
        }
        LinearLayout editing=PhoneUi.row(this);
        editing.addView(secondary("+",()->number.append("+")),PhoneUi.weight(this));
        editing.addView(secondary("Delete",()->{
            int start=number.getSelectionStart();
            if(start>0)number.getText().delete(start-1,start);
        }),PhoneUi.weight(this));
        editing.addView(secondary("Clear",()->number.setText("")),PhoneUi.weight(this));
        dialCard.addView(editing,PhoneUi.margins(this,6,13));
        dialCard.addView(dial);
        target.addView(dialCard);
        target.addView(PhoneUi.label(this,
                "Calls still use your main phone's SIM. Tap Start voice during a call to try encrypted companion audio. Phone compatibility is not yet verified.",
                12,PhoneUi.MUTED,false),PhoneUi.margins(this,11,0));
    }

    private void showContacts() {
        tabContent.addView(PhoneUi.label(this,"Contacts",22,PhoneUi.INK,true),PhoneUi.margins(this,0,10));
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            tabContent.addView(text("Use contacts saved on this device. Nothing is uploaded to the main phone.", 14));
            tabContent.addView(button("Allow contacts", () ->
                    requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 44)));
            return;
        }
        search = input("Search contacts", InputType.TYPE_CLASS_TEXT);
        tabContent.addView(search,PhoneUi.margins(this,0,12));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderContacts(s.toString());
            }
            @Override public void afterTextChanged(Editable e) { }
        });
        if (!contactsLoaded) loadContacts();
        else renderContacts("");
    }

    private void loadContacts() {
        contactsLoaded = true;
        tabContent.addView(text("Loading contacts...", 14));
        worker.execute(() -> {
            ArrayList<Contact> result = new ArrayList<>();
            try {
                String[] columns = {
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                };
                try (Cursor cursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        columns, null, null,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
                    if (cursor != null) {
                        while (cursor.moveToNext() && result.size() < 1500) {
                            String label = cursor.getString(0);
                            String phone = cursor.getString(1);
                            if (label != null && phone != null)
                                result.add(new Contact(label, phone));
                        }
                    }
                }
            } catch (Exception ignored) { }
            runOnUiThread(() -> {
                contacts.clear();
                contacts.addAll(result);
                if ("Contacts".equals(tab)) renderContacts(search == null ? "" : search.getText().toString());
            });
        });
    }

    private void renderContacts(String filter) {
        if (tabContent == null || !"Contacts".equals(tab)) return;
        // Keep the search field and replace only the result rows.
        while (tabContent.getChildCount() > 2) tabContent.removeViewAt(2);
        String query = filter.toLowerCase().trim();
        int count = 0;
        for (Contact c : contacts) {
            if (!query.isEmpty() && !c.name.toLowerCase().contains(query) &&
                    !c.phone.contains(query)) continue;
            addContactRow(tabContent, c.name, c.phone);
            if (++count == 35) break;
        }
        if (count == 0) tabContent.addView(text("No matching contacts.", 14));
        if (count == 35) tabContent.addView(text("Type a name to narrow the results.", 12));
    }

    private void addContactRow(LinearLayout parent, String name, String phone) {
        LinearLayout row = row();
        row.setPadding(0,dp(5),0,dp(5));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView who = text(name, 15);
        who.setTypeface(null,Typeface.BOLD);
        labels.addView(who);
        labels.addView(text(phone, 13));
        row.addView(labels, new LinearLayout.LayoutParams(0,-2,2f));
        Button favorite = button(isFavorite(phone) ? "★" : "☆", () -> {
            toggleFavorite(phone);
            selectTab(tab);
        });
        favorite.setTextSize(19);
        row.addView(favorite, new LinearLayout.LayoutParams(dp(49),-2));
        row.addView(button("Call", () -> {
            number.setText(phone);
            command("dial",phone);
        }));
        parent.addView(row);
    }

    private void showFavorites() {
        tabContent.addView(PhoneUi.label(this,"Favorites",22,PhoneUi.INK,true),PhoneUi.margins(this,0,12));
        Set<String> favorites = prefs().getStringSet("favorite_numbers", new HashSet<>());
        if (favorites.isEmpty()) {
            tabContent.addView(text("Star contacts to keep them here.", 14));
            return;
        }
        for (String phone : favorites) {
            String name = phone;
            for (Contact c : contacts) {
                if (c.phone.equals(phone)) { name = c.name; break; }
            }
            addContactRow(tabContent,name,phone);
        }
    }

    private void showRecents() {
        tabContent.addView(PhoneUi.label(this,"Recent call requests",22,PhoneUi.INK,true),PhoneUi.margins(this,0,8));
        tabContent.addView(text("Recent call requests from this device, not the main phone's call history.", 12));
        JSONArray recent;
        try { recent = new JSONArray(prefs().getString("recent", "[]")); }
        catch (Exception e) { recent = new JSONArray(); }
        for (int i = 0; i < recent.length() && i < 20; i++) {
            String phone = recent.optString(i);
            if (phone.isEmpty()) continue;
            LinearLayout row = row();
            row.addView(text(phone,16),new LinearLayout.LayoutParams(0,-2,1f));
            row.addView(button("Call again", () -> {
                number.setText(phone);
                command("dial",phone);
            }));
            tabContent.addView(row);
        }
        if (recent.length() == 0) tabContent.addView(text("No calls placed yet.",14));
    }

    private void toggleFavorite(String phone) {
        Set<String> favorites = new HashSet<>(prefs().getStringSet("favorite_numbers",new HashSet<>()));
        if (!favorites.add(phone)) favorites.remove(phone);
        prefs().edit().putStringSet("favorite_numbers",favorites).apply();
    }

    private boolean isFavorite(String phone) {
        return prefs().getStringSet("favorite_numbers", new HashSet<>()).contains(phone);
    }

    private void remember(String phone) {
        if (phone == null || phone.trim().isEmpty()) return;
        try {
            JSONArray previous = new JSONArray(prefs().getString("recent","[]"));
            JSONArray next = new JSONArray().put(phone);
            for (int i=0; i<previous.length() && next.length()<20; i++) {
                String item = previous.optString(i);
                if (!phone.equals(item) && !item.isEmpty()) next.put(item);
            }
            prefs().edit().putString("recent",next.toString()).apply();
        } catch (Exception ignored) { }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("companion_ui", MODE_PRIVATE);
    }

    private void command(String action, String phone) {
        if (note != null) note.setText("dtmf".equals(action) ? "Sending keypad tone..." : "Sending...");
        worker.execute(() -> {
            try {
                RemoteClient.call(this, action, phone);
                runOnUiThread(() -> {
                    if ("dial".equals(action)) remember(phone);
                    if (note != null) note.setText(
                            "dial".equals(action) ? "Call requested on your main phone."
                            : "dtmf".equals(action) ? "Tone sent." : "Request sent.");
                    fetchStatus();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (note != null) note.setText(e.getMessage()); });
            }
        });
    }

    private void fetchStatus() {
        if (!RemoteClient.isPaired(this) || polling) return;
        polling = true;
        worker.execute(() -> {
            try {
                JSONObject state = RemoteClient.status(this);
                runOnUiThread(() -> {
                    if (connection == null) return;
                    String call = state.optString("callState","unknown");
                    activeState = call;
                    dtmfReady = state.optBoolean("dtmfReady", false);
                    connection.setText(
                            "Connected  |  " + state.optString("model","Main phone") +
                            ("idle".equals(call) ? "  |  Ready" :
                            "ringing".equals(call) ? "  |  Ringing" :
                            "active".equals(call) ? "  |  On a call" : "  |  " + call));
                    connection.setTextColor(PhoneUi.FOREST);
                    if(callCaption!=null)callCaption.setText("ringing".equals(call)
                            ? "Incoming call. Answer, then tap Start voice to use this device."
                            : "active".equals(call)
                            ? (VoiceSessionService.active()
                                ? "Encrypted audio connected. Check that both people can hear each other."
                                : "Call active. Tap Start voice to try remote audio.")
                            : "Ready to place a cellular call using the main phone.");
                    if(callControls!=null)callControls.setVisibility(
                            "ringing".equals(call)||"active".equals(call)?View.VISIBLE:View.GONE);
                    if (answer != null) answer.setEnabled("ringing".equals(call));
                    if (end != null) end.setEnabled("ringing".equals(call) || "active".equals(call));
                    if (dial != null) dial.setEnabled("idle".equals(call));
                    updateVoiceButtons();
                    if(voiceStatus!=null && "active".equals(call))
                        voiceStatus.setText(VoiceSessionService.state());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (connection != null) {
                        connection.setText("Offline  |  Check main phone Wi-Fi");
                        connection.setTextColor(PhoneUi.RED);
                    }
                    if(callCaption!=null)callCaption.setText("Your saved pairing is still here. Reconnect when the host is online.");
                    if(callControls!=null)callControls.setVisibility(View.GONE);
                    if (dial != null) dial.setEnabled(false);
                    if (answer != null) answer.setEnabled(false);
                    if (end != null) end.setEnabled(false);
                    updateVoiceButtons();
                });
            } finally {
                polling = false;
            }
        });
    }

    private void toggleAlerts() {
        boolean enabled = !prefs().getBoolean("alerts",false);
        prefs().edit().putBoolean("alerts",enabled).apply();
        if (enabled) {
            if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},43);
            } else startAlertsIfEnabled();
        } else {
            stopService(new Intent(this, CompanionService.class));
        }
        updateAlertsButton();
    }

    private void startAlertsIfEnabled() {
        if (!RemoteClient.isPaired(this)) return;
        if (!prefs().getBoolean("alerts",false)) return;
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) return;
        try { startForegroundService(new Intent(this, CompanionService.class)); }
        catch (Exception error) {
            if (note != null) note.setText("Could not enable call alerts: " + error.getMessage());
        }
    }

    private void updateAlertsButton() {
        if (alerts != null) alerts.setText(prefs().getBoolean("alerts",false) ?
                "Turn off incoming alerts" : "Enable incoming alerts");
    }

    private void changeRole() {
        stopService(new Intent(this, CompanionService.class));
        stopService(new Intent(this, VoiceSessionService.class));
        getSharedPreferences("mode",MODE_PRIVATE).edit().remove("role").apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override public void onRequestPermissionsResult(int request, String[] perms, int[] results) {
        super.onRequestPermissionsResult(request, perms, results);
        if (request == 43) startAlertsIfEnabled();
        if (request == 47 && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) startVoice();
        if (request == 44 && checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            contactsLoaded = false;
            selectTab("Contacts");
        }
    }

    private LinearLayout row() {
        return PhoneUi.row(this);
    }

    private LinearLayout.LayoutParams weighted() {
        return PhoneUi.weight(this);
    }

    private TextView text(String value,int sp) {
        TextView view=PhoneUi.label(this,value,sp,PhoneUi.INK,false);
        view.setPadding(dp(3),dp(6),dp(3),dp(6));
        return view;
    }

    private EditText input(String hint,int type) {
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setTextSize(16);
        field.setTextColor(PhoneUi.INK);
        field.setHintTextColor(PhoneUi.MUTED);
        field.setHint(hint);
        field.setInputType(type);
        field.setPadding(dp(13),dp(13),dp(13),dp(13));
        field.setBackground(PhoneUi.background(this,PhoneUi.BG,13,PhoneUi.BORDER));
        return field;
    }

    private Button button(String value,Runnable action) {
        Button view=PhoneUi.button(this,value,PhoneUi.CARD,PhoneUi.FOREST,android.graphics.Color.TRANSPARENT);
        view.setOnClickListener(v->action.run());
        return view;
    }

    private Button secondary(String value,Runnable action) {
        Button view=PhoneUi.button(this,value,PhoneUi.FOREST,PhoneUi.PALE,android.graphics.Color.TRANSPARENT);
        view.setOnClickListener(v->action.run());
        return view;
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
}
