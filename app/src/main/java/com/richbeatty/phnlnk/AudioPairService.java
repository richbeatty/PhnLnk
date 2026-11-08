package com.richbeatty.phnlnk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The system's pairing code dialog stays open in Settings while the user
 * types the code into this foreground notification.
 */
public final class AudioPairService extends Service {
    static final String START = "com.richbeatty.phnlnk.audio.PAIR_BEGIN";
    private static final String SUBMIT = "com.richbeatty.phnlnk.audio.PAIR_SUBMIT";
    private static final String FIELD = "phnlnk_self_adb_code";
    private static final String CHANNEL = "phnlnk_self_adb_pairing";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Audio access setup", NotificationManager.IMPORTANCE_HIGH));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = build("Open Android's Wireless debugging pairing code dialog.", true);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(918, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(918, notification);
        }

        if (intent != null && SUBMIT.equals(intent.getAction())) {
            CharSequence input = RemoteInput.getResultsFromIntent(intent) == null ? null :
                    RemoteInput.getResultsFromIntent(intent).getCharSequence(FIELD);
            String code = input == null ? "" : input.toString().trim();
            if (!code.matches("[0-9]{6}")) {
                notifyStatus("Enter the six-digit Android pairing code.", true);
                return START_NOT_STICKY;
            }
            notifyStatus("Pairing with Android on this phone...", false);
            worker.execute(() -> {
                try {
                    String result = new HostAdb(this).pair(code);
                    getSharedPreferences("phnlnk_audio_bootstrap", MODE_PRIVATE)
                            .edit().putBoolean("adb_paired", true).apply();
                    notifyStatus(result + " Open PhnLnk to test audio.", false);
                    // Notification remains visible after the service is stopped.
                    stopForeground(Service.STOP_FOREGROUND_DETACH);
                    stopSelf(startId);
                } catch (Exception error) {
                    Log.w("PhnLnkAudio", "Pairing failed", error);
                    notifyStatus("Pairing failed: " + safe(error.getMessage()), true);
                }
            });
        }
        return START_NOT_STICKY;
    }

    private Notification build(String message, boolean allowReply) {
        Intent screen = new Intent(this, HostAudioActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 919, screen,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentTitle("PhnLnk audio setup")
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setOngoing(allowReply);
        if (allowReply) {
            RemoteInput remote = new RemoteInput.Builder(FIELD)
                    .setLabel("Six-digit Android pairing code").build();
            PendingIntent send = PendingIntent.getForegroundService(this, 920,
                    new Intent(this, AudioPairService.class).setAction(SUBMIT),
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_menu_send, "Enter code", send)
                    .addRemoteInput(remote).build());
        }
        return builder.build();
    }

    private void notifyStatus(String status, boolean allowReply) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(918, build(status, allowReply));
    }

    private static String safe(String input) {
        if (input == null) return "Unknown error";
        return input.length() > 130 ? input.substring(0, 130) : input;
    }

    @Override public void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {return null;}
}
