package com.richbeatty.phnlnk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

public final class GatewayService extends Service {
    private static final String CHANNEL = "phnlnk_host";
    private static volatile boolean running = false;
    private HttpBridge bridge;

    static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "PhnLnk host", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows when local call control is enabled");
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_STICKY;

        Intent appIntent = new Intent(this, MainActivity.class);
        PendingIntent tap = PendingIntent.getActivity(this, 0, appIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("PhnLnk host active")
                .setContentText("Private call-control interface enabled")
                .setContentIntent(tap)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(81, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(81, notification);
        }

        try {
            bridge = new HttpBridge(this);
            bridge.start();
            running = true;
        } catch (Exception exception) {
            Toast.makeText(this, "PhnLnk: " + exception.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (bridge != null) {
            bridge.close();
            bridge = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
