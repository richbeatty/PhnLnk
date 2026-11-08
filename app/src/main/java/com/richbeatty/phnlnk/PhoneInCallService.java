package com.richbeatty.phnlnk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.InCallService;

/**
 * Optional default-dialer integration. Android only provides public Call DTMF
 * APIs to the InCallService that is actively bound as the default phone app.
 */
public final class PhoneInCallService extends InCallService {
    private static final String CHANNEL = "phnlnk_host_call_v1";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Call activeCall;
    private static volatile PhoneInCallService instance;
    private final Call.Callback observer = new Call.Callback() {
        @Override public void onStateChanged(Call call, int state) {
            postCallNotification();
        }
    };

    static boolean hasCall() {
        return activeCall != null && activeCall.getState() != Call.STATE_DISCONNECTED;
    }

    static boolean canSendDtmf() {
        return activeCall != null && activeCall.getState() == Call.STATE_ACTIVE;
    }

    static boolean isRinging() {
        return activeCall != null && activeCall.getState() == Call.STATE_RINGING;
    }

    static void sendDtmf(char digit) {
        if ("0123456789*#".indexOf(digit) < 0)
            throw new IllegalArgumentException("Only digits, star and hash are supported");
        if (!canSendDtmf())
            throw new IllegalStateException(
                    "In-call keypad needs PhnLnk set as the host phone's default dialer");
        MAIN.post(() -> {
            Call target = activeCall;
            if (target == null || target.getState() != Call.STATE_ACTIVE) return;
            target.playDtmfTone(digit);
            MAIN.postDelayed(() -> {
                try { target.stopDtmfTone(); }
                catch (Exception ignored) { }
            }, 190);
        });
    }

    static void answerCall() {
        MAIN.post(() -> {
            Call call = activeCall;
            if (call != null && call.getState() == Call.STATE_RINGING) call.answer(0);
        });
    }

    static void endCall() {
        MAIN.post(() -> {
            Call call = activeCall;
            if (call != null && call.getState() != Call.STATE_DISCONNECTED) call.disconnect();
        });
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        NotificationManager notifications = getSystemService(NotificationManager.class);
        if (notifications != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "Host phone calls", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Incoming and ongoing call controls when PhnLnk is the default phone app");
            notifications.createNotificationChannel(channel);
        }
    }

    @Override public void onCallAdded(Call call) {
        super.onCallAdded(call);
        activeCall = call;
        call.registerCallback(observer);
        postCallNotification();
    }

    @Override public void onCallRemoved(Call call) {
        call.unregisterCallback(observer);
        if (activeCall == call) activeCall = null;
        postCallNotification();
        super.onCallRemoved(call);
    }

    private void postCallNotification() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(this::postCallNotification);
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        Call call = activeCall;
        if (call == null || call.getState() == Call.STATE_DISCONNECTED) {
            manager.cancel(882);
            return;
        }
        boolean ringing = call.getState() == Call.STATE_RINGING;
        Intent open = new Intent(this, HostCallActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent intent = PendingIntent.getActivity(
                this, 31, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle(ringing ? "Incoming call" : "Call in progress")
                .setContentText("Tap to open the host phone controls")
                .setCategory(Notification.CATEGORY_CALL)
                .setContentIntent(intent)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

        // Do NOT setFullScreenIntent or launch a screen. This is deliberately
        // notification-only so PhnLnk itself does not wake the host display.
        if (ringing) {
            Intent answer = new Intent(this, HostCallActionReceiver.class)
                    .setAction(HostCallActionReceiver.ANSWER);
            Intent reject = new Intent(this, HostCallActionReceiver.class)
                    .setAction(HostCallActionReceiver.END);
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_menu_call, "Answer",
                    PendingIntent.getBroadcast(this, 34, answer,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build());
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, "Decline",
                    PendingIntent.getBroadcast(this, 35, reject,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build());
        }
        manager.notify(882, builder.build());
    }

    @Override public void onDestroy() {
        instance = null;
        activeCall = null;
        NotificationManager notifications = getSystemService(NotificationManager.class);
        if (notifications != null) notifications.cancel(882);
        super.onDestroy();
    }
}
