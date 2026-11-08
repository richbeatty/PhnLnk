package com.richbeatty.phnlnk;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/** Opt-in, continuous SIP registration and microphone-capable voice service. */
public final class SipVoiceService extends Service {
    private static final String CHANNEL = "phnlnk_sip_status_v1";
    private static final String INCOMING = "phnlnk_sip_incoming_v1";
    private static volatile SipVoiceService active;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean previouslyRinging;
    private final Runnable stateTick = new Runnable() {
        @Override public void run() {
            if (active != SipVoiceService.this) return;
            SipVoiceEngine engine = SipVoiceEngine.current();
            boolean ringing = engine != null && engine.isIncoming();
            if (ringing && !previouslyRinging) alertIncomingCall();
            if (!ringing && previouslyRinging) {
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) manager.cancel(559);
            }
            previouslyRinging = ringing;
            handler.postDelayed(this, 750);
        }
    };

    static void notifyUpdate() {
        SipVoiceService service = active;
        if (service != null) service.handler.post(service::refreshForeground);
    }

    @Override public void onCreate() {
        super.onCreate();
        active = this;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "Voice bridge connection", NotificationManager.IMPORTANCE_LOW));
            manager.createNotificationChannel(new NotificationChannel(INCOMING,
                    "Incoming voice calls", NotificationManager.IMPORTANCE_HIGH));
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!VoiceCredentials.enabled(this) ||
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Intent open = new Intent(this, VoiceBridgeActivity.class);
        PendingIntent tap = PendingIntent.getActivity(this, 557, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("PhnLnk voice bridge")
                .setContentText("Connecting to Bluetooth-to-SIP gateway")
                .setOngoing(true)
                .setContentIntent(tap)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(556, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(556, notification);
            }
            SipVoiceEngine.create(this);
            handler.removeCallbacks(stateTick);
            handler.post(stateTick);
            refreshForeground();
            return START_STICKY;
        } catch (Exception error) {
            Log.e("PhnLnk", "Could not start Bluetooth voice bridge", error);
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private void refreshForeground() {
        SipVoiceEngine engine = SipVoiceEngine.current();
        if (engine == null) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        PendingIntent tap = PendingIntent.getActivity(this, 557,
                new Intent(this, VoiceBridgeActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("PhnLnk voice bridge")
                .setContentText(engine.callStatus().contains("No voice call")
                        ? engine.status() : engine.callStatus())
                .setContentIntent(tap)
                .setOngoing(true).build();
        manager.notify(556, notification);
    }

    private void alertIncomingCall() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        Intent open = new Intent(this, VoiceBridgeActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(this, 558, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification incoming = new Notification.Builder(this, INCOMING)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("Incoming call through PhnLnk")
                .setContentText("Tap to answer on this Android device")
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build();
        manager.notify(559, incoming);
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(stateTick);
        SipVoiceEngine.shutdown();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(559);
        active = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {return null;}
}
