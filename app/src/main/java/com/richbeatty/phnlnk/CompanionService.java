package com.richbeatty.phnlnk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * Opt-in companion-side status watcher. This cannot forward call audio.
 * It posts an incoming-call alert while connected to the host over private Wi-Fi.
 */
public final class CompanionService extends Service {
    private static final String MONITOR = "phnlnk_companion_monitor";
    private static final String INCOMING = "phnlnk_incoming";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean ringing;
    private boolean stopped;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (stopped || !RemoteClient.isPaired(CompanionService.this)) return;
            worker.execute(() -> {
                boolean current = false;
                try {
                    JSONObject status = RemoteClient.status(CompanionService.this);
                    current = "ringing".equals(status.optString("callState"));
                } catch (Exception ignored) {
                    // A temporarily unreachable host must never become a false incoming call.
                }
                final boolean isRinging = current;
                handler.post(() -> {
                    if (stopped) return;
                    if (isRinging && !ringing) announceCall();
                    if (!isRinging && ringing) {
                        NotificationManager manager = getSystemService(NotificationManager.class);
                        if (manager != null) manager.cancel(204);
                    }
                    ringing = isRinging;
                    handler.postDelayed(poll, 3500);
                });
            });
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(new NotificationChannel(
                    MONITOR, "Phone connection", NotificationManager.IMPORTANCE_LOW));
            manager.createNotificationChannel(new NotificationChannel(
                    INCOMING, "Incoming call alerts", NotificationManager.IMPORTANCE_HIGH));
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!RemoteClient.isPaired(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        stopped = false;
        Intent open = new Intent(this, ClientActivity.class);
        PendingIntent tap = PendingIntent.getActivity(this, 2, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification persistent = new Notification.Builder(this, MONITOR)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("PhnLnk is watching for calls")
                .setContentText("Connected over your private network")
                .setContentIntent(tap)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(203, persistent, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(203, persistent);
        }
        handler.removeCallbacks(poll);
        handler.post(poll);
        return START_STICKY;
    }

    private void announceCall() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        Intent open = new Intent(this, ClientActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(this, 3, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification call = new Notification.Builder(this, INCOMING)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("Incoming call on your phone")
                .setContentText("Tap to open call controls. Audio stays on the host phone.")
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(tap)
                .build();
        manager.notify(204, call);
    }

    @Override public void onDestroy() {
        stopped = true;
        handler.removeCallbacks(poll);
        worker.shutdownNow();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(204);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
