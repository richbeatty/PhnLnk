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
    private TextView connection, note;
    private EditText number, search;
    private Button dial, answer, end, alerts;
    private String tab = "Dial";
    private boolean contactsLoaded;
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
        ScrollView scrolling = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(17);
        root.setPadding(pad,pad,pad,pad);
        scrolling.addView(root);
        setContentView(scrolling);
        TextView brand = text("PhnLnk", 30);
        brand.setTypeface(null, Typeface.BOLD);
        root.addView(brand);
        root.addView(text("Your phone. Connected.", 14));
    }

    private void clearScreen() {
        while (root.getChildCount() > 2) root.removeViewAt(2);
        connection = null;
        note = null;
        tabContent = null;
    }

    private void showPair(String ip, String code) {
        clearScreen();
        root.addView(text("Connect to your main phone", 20));
        root.addView(text("Open PhnLnk on the phone with your SIM, tap Pair a device, then enter the address and six-digit code.", 14));
        EditText address = input("Phone Wi-Fi address (192.168.1.10)", InputType.TYPE_CLASS_TEXT);
        address.setText(ip);
        root.addView(address);
        EditText digits = input("Six-digit pairing code", InputType.TYPE_CLASS_NUMBER);
        digits.setText(code);
        root.addView(digits);
        note = text("", 14);
        root.addView(button("Connect", () -> connect(address.getText().toString(), digits.getText().toString())));
        root.addView(note);
        root.addView(text("Both phones must be on the same trusted Wi-Fi network. Scanning the QR on the main phone fills these details automatically.", 13));
        root.addView(button("Make this the main phone instead", () -> changeRole()));
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
        connection = text("Checking phone...", 16);
        root.addView(connection);
        LinearLayout actions = row();
        answer = button("Answer", () -> command("answer", ""));
        end = button("End call", () -> command("end", ""));
        actions.addView(answer, weighted());
        actions.addView(end, weighted());
        root.addView(actions);
        root.addView(text("Call control only. You still need your main phone to hear and speak.", 12));

        number = input("Enter a number", InputType.TYPE_CLASS_PHONE);
        number.setTextSize(24);
        root.addView(number);
        dial = button("Call using my number", () -> command("dial", number.getText().toString()));
        root.addView(dial);

        LinearLayout tabs = row();
        for (String item : new String[]{"Dial", "Contacts", "Favorites", "Recents"}) {
            Button control = button(item, () -> selectTab(item));
            control.setTextSize(12);
            control.setPadding(dp(4),dp(7),dp(4),dp(7));
            tabs.addView(control, weighted());
        }
        root.addView(tabs);
        tabContent = new LinearLayout(this);
        tabContent.setOrientation(LinearLayout.VERTICAL);
        root.addView(tabContent);
        selectTab("Dial");

        root.addView(text("Incoming call alerts", 19));
        alerts = button("Enable alerts", () -> toggleAlerts());
        root.addView(alerts);
        root.addView(button("Disconnect this device", () -> {
            new AlertDialog.Builder(this)
                    .setTitle("Disconnect from your phone?")
                    .setPositiveButton("Disconnect", (dialog, which) -> {
                        stopService(new Intent(this, CompanionService.class));
                        RemoteClient.forget(this);
                        showPair("", "");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }));
        root.addView(button("Use this device as the main phone instead", () -> changeRole()));
        note = text("",14);
        root.addView(note);
        updateAlertsButton();
        fetchStatus();
    }

    private void selectTab(String name) {
        tab = name;
        if (tabContent == null) return;
        tabContent.removeAllViews();
        if ("Dial".equals(name)) {
            addKeypad(tabContent);
        } else if ("Contacts".equals(name)) {
            showContacts();
        } else if ("Favorites".equals(name)) {
            showFavorites();
        } else {
            showRecents();
        }
    }

    private void addKeypad(LinearLayout target) {
        String[][] keys = {
                {"1","2","3"}, {"4","5","6"}, {"7","8","9"}, {"*","0","#"}
        };
        for (String[] rowKeys : keys) {
            LinearLayout row = row();
            for (String key : rowKeys) {
                Button button = button(key, () -> number.append(key));
                button.setTextSize(25);
                button.setAllCaps(false);
                row.addView(button, weighted());
            }
            target.addView(row);
        }
        LinearLayout row = row();
        row.addView(button("+", () -> number.append("+")), weighted());
        row.addView(button("Delete", () -> {
            int start = number.getSelectionStart();
            if (start > 0) number.getText().delete(start - 1,start);
        }), weighted());
        row.addView(button("Clear", () -> number.setText("")), weighted());
        target.addView(row);
        target.addView(text("Dial pad is for entering a number. During-call keypad tones are not supported in this version.", 12));
    }

    private void showContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            tabContent.addView(text("Use contacts saved on this device. Nothing is uploaded to the main phone.", 14));
            tabContent.addView(button("Allow contacts", () ->
                    requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 44)));
            return;
        }
        search = input("Search contacts", InputType.TYPE_CLASS_TEXT);
        tabContent.addView(search);
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
        while (tabContent.getChildCount() > 1) tabContent.removeViewAt(1);
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
        if (note != null) note.setText("Sending...");
        worker.execute(() -> {
            try {
                RemoteClient.call(this, action, phone);
                runOnUiThread(() -> {
                    if ("dial".equals(action)) remember(phone);
                    if (note != null) note.setText(
                            "dial".equals(action) ? "Call requested on your main phone." : "Request sent.");
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
                    connection.setText("Connected to " + state.optString("model","your phone")
                            + "\nCall status: " + call
                            + ("ringing".equals(call) ? "\nIncoming call on your main phone" : ""));
                    if (answer != null) answer.setEnabled("ringing".equals(call));
                    if (end != null) end.setEnabled("ringing".equals(call) || "active".equals(call));
                    if (dial != null) dial.setEnabled("idle".equals(call));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (connection != null) connection.setText("Offline. Check the main phone and Wi-Fi.");
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
        getSharedPreferences("mode",MODE_PRIVATE).edit().remove("role").apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override public void onRequestPermissionsResult(int request, String[] perms, int[] results) {
        super.onRequestPermissionsResult(request, perms, results);
        if (request == 43) startAlertsIfEnabled();
        if (request == 44 && checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            contactsLoaded = false;
            selectTab("Contacts");
        }
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,-2,1f);
        p.setMargins(dp(2),dp(3),dp(2),dp(3));
        return p;
    }

    private TextView text(String value,int sp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setPadding(dp(3),dp(6),dp(3),dp(6));
        return view;
    }

    private EditText input(String hint,int type) {
        EditText view = new EditText(this);
        view.setSingleLine(true);
        view.setTextSize(17);
        view.setHint(hint);
        view.setInputType(type);
        return view;
    }

    private Button button(String value,Runnable action) {
        Button view = new Button(this);
        view.setAllCaps(false);
        view.setText(value);
        view.setTextSize(15);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
}
