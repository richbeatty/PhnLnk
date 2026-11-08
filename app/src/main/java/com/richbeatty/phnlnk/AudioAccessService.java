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
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Opt-in host-side reconnection for the experimental shell audio helper.
 * It NEVER enables Wireless Debugging, runs a real audio bridge, or wakes UI.
 * A permanent Android ADB pairing is reused only while wireless ADB is on.
 */
public final class AudioAccessService extends Service {
    static final String ACTION_RETRY = "com.richbeatty.phnlnk.audio.RETRY";
    static final String ACTION_STOP = "com.richbeatty.phnlnk.audio.STOP";

    private static final String CHANNEL = "phnlnk_audio_access_v1";
    private static final int NOTICE = 936;
    private static final long SUCCESS_POLL_MS = 60000;
    private static final long FAILURE_RETRY_MS = 90000;
    private static final AtomicBoolean CONNECTING = new AtomicBoolean(false);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean stopped;
    private final Runnable check = new Runnable() {
        @Override public void run() {
            if (stopped || !AudioAccessState.autoReconnect(AudioAccessService.this)) return;
            if (!CONNECTING.compareAndSet(false, true)) return;
            worker.execute(() -> {
                boolean ready = false;
                try {
                    // Probe an existing helper first. Only launch via embedded
                    // ADB when a saved pairing already exists and ADB is on.
                    String reply;
                    try { reply = AudioProbeClient.request(AudioAccessService.this,"PING"); }
                    catch (Exception ignored) { reply = ""; }
                    if (!reply.startsWith("READY:V3:")) {
                        if (!AudioAccessState.isPaired(AudioAccessService.this)) {
                            AudioAccessState.set(AudioAccessService.this,
                                    AudioAccessState.NEEDS_PAIRING,
                                    "Android has not been paired yet. Start guided setup.");
                        } else {
                            AudioAccessState.set(AudioAccessService.this,
                                    AudioAccessState.CONNECTING, "Looking for authorized Android debugging...");
                            reply = new HostAdb(AudioAccessService.this).startProbe(step ->
                                    AudioAccessState.set(AudioAccessService.this,
                                            AudioAccessState.CONNECTING, step));
                        }
                    }
                    ready = reply.startsWith("READY:V3:");
                    if (ready) {
                        AudioAccessState.set(AudioAccessService.this,
                                AudioAccessState.READY,
                                "Cellular audio bridge process ready. Host capture and uplink still require real call tests.");
                    }
                } catch (Exception error) {
                    String msg = error.getMessage() == null ? "Unknown connection error" : error.getMessage();
                    if (msg.contains("Wireless debugging") ||
                            msg.contains("Wireless Debugging") ||
                            msg.contains("connection port") ||
                            msg.contains("stopped responding") ||
                            msg.contains("not running")) {
                        AudioAccessState.set(AudioAccessService.this,
                                AudioAccessState.DEBUGGING_OFF,
                                "Android's debugging port could not be reached. Confirm Wireless Debugging is enabled. If discovery fails, enter the port shown in Settings.");
                    } else {
                        Log.w("PhnLnkAudio","Automatic helper connection failed",error);
                        AudioAccessState.set(AudioAccessService.this,
                                AudioAccessState.ERROR,
                                "Connection failed: " + (msg.length() > 140 ? msg.substring(0,140) : msg));
                    }
                } finally {
                    CONNECTING.set(false);
                    final boolean nextReady = ready;
                    main.post(() -> {
                        if (stopped) return;
                        refreshNotification();
                        main.removeCallbacks(check);
                        main.postDelayed(check, nextReady ? SUCCESS_POLL_MS : FAILURE_RETRY_MS);
                    });
                }
            });
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(new NotificationChannel(
                CHANNEL,"PhnLnk audio access",NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            AudioAccessState.disable(this);
            stopped = true;
            main.removeCallbacks(check);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!AudioAccessState.autoReconnect(this) || !AudioAccessState.isPaired(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        stopped = false;
        Notification initial = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTICE, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTICE,initial);
        }
        main.removeCallbacks(check);
        main.post(check);
        return START_STICKY;
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this,937,
                new Intent(this,HostAudioActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("PhnLnk audio setup")
                .setContentText(AudioAccessState.display(this))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void refreshNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTICE,buildNotification());
    }

    @Override public void onDestroy() {
        stopped = true;
        main.removeCallbacks(check);
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
