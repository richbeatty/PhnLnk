package com.richbeatty.phnlnk;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import org.json.JSONObject;

final class CallController {
    private CallController() { }

    static JSONObject status(Context context) throws Exception {
        JSONObject status = new JSONObject();
        status.put("host", GatewayService.isRunning());
        status.put("model", Build.MANUFACTURER + " " + Build.MODEL);
        status.put("android", Build.VERSION.RELEASE);
        status.put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown");
        status.put("callPermission", has(context, Manifest.permission.CALL_PHONE));
        status.put("answerPermission", has(context, Manifest.permission.ANSWER_PHONE_CALLS));
        status.put("readPermission", has(context, Manifest.permission.READ_PHONE_STATE));
        status.put("hasTelephony", context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
        status.put("audioRelayed", false);
        status.put("voiceTransportActive", VoiceRelayServer.isBusy());
        status.put("voiceStage", VoiceRelayServer.lastStage());
        status.put("voiceLastError", VoiceRelayServer.lastError());
        status.put("voiceLastErrorAt", VoiceRelayServer.lastErrorAt());
        status.put("voiceHostHelperReady", AudioAccessState.READY.equals(
                AudioAccessState.state(context)));
        status.put("voiceTransportSupported", true); // Firmware audio still unverified.
        status.put("dtmfReady", PhoneInCallService.canSendDtmf());
        status.put("defaultDialerRequiredForDtmf", !PhoneInCallService.canSendDtmf());

        String callState = "permission needed";
        if (has(context, Manifest.permission.READ_PHONE_STATE)) {
            TelephonyManager phone = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (phone != null) {
                switch (phone.getCallState()) {
                    case TelephonyManager.CALL_STATE_IDLE: callState = "idle"; break;
                    case TelephonyManager.CALL_STATE_RINGING: callState = "ringing"; break;
                    case TelephonyManager.CALL_STATE_OFFHOOK: callState = "active"; break;
                    default: callState = "unknown";
                }
            }
        }
        status.put("callState", callState);

        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        boolean telephonyRouteVisible = false;
        if (audio != null) {
            for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_ALL)) {
                if (device.getType() == AudioDeviceInfo.TYPE_TELEPHONY) {
                    telephonyRouteVisible = true;
                    break;
                }
            }
        }
        status.put("telephonyDeviceVisible", telephonyRouteVisible);
        // Device enumeration is NOT a validation of actual two-way telephony capture.
        return status;
    }

    static void dial(Context context, String input) throws Exception {
        if (!has(context, Manifest.permission.CALL_PHONE)) {
            throw new IllegalStateException("Grant the Call phone permission on the host phone");
        }
        String number = normalizedNanp(input);
        TelecomManager telecom = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        if (telecom == null) throw new IllegalStateException("Android Telecom is unavailable");
        telecom.placeCall(Uri.fromParts("tel", number, null), new Bundle());
    }

    static void answer(Context context) {
        if (!has(context, Manifest.permission.ANSWER_PHONE_CALLS)) {
            throw new IllegalStateException("Grant the Answer phone calls permission on the host phone");
        }
        if (!"ringing".equals(currentState(context))) {
            throw new IllegalStateException("No incoming call is ringing");
        }
        TelecomManager telecom = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        if (telecom == null) throw new IllegalStateException("Android Telecom is unavailable");
        telecom.acceptRingingCall();
    }

    static void end(Context context) {
        if (!has(context, Manifest.permission.ANSWER_PHONE_CALLS)) {
            throw new IllegalStateException("Grant the Answer phone calls permission on the host phone");
        }
        TelecomManager telecom = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        if (telecom == null) throw new IllegalStateException("Android Telecom is unavailable");
        if (!telecom.endCall()) {
            throw new IllegalStateException("No call could be ended");
        }
    }

    private static String currentState(Context context) {
        if (!has(context, Manifest.permission.READ_PHONE_STATE)) return "permission needed";
        TelephonyManager phone = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (phone == null) return "unknown";
        int state = phone.getCallState();
        if (state == TelephonyManager.CALL_STATE_RINGING) return "ringing";
        if (state == TelephonyManager.CALL_STATE_OFFHOOK) return "active";
        return "idle";
    }

    private static boolean has(Context c, String permission) {
        return c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static String normalizedNanp(String userInput) {
        if (userInput == null || !userInput.matches("[+0-9 ()\\-.]{10,24}")) {
            throw new IllegalArgumentException("Enter a 10-digit US or Canadian number");
        }
        String digits = userInput.replaceAll("[^0-9]", "");
        if (digits.length() == 11 && digits.charAt(0) == '1') digits = digits.substring(1);
        if (digits.length() != 10 || digits.charAt(0) < '2' || digits.charAt(3) < '2') {
            throw new IllegalArgumentException("Only 10-digit +1 calls are supported");
        }
        if (digits.startsWith("900")) {
            throw new IllegalArgumentException("Premium-rate 900 numbers are blocked");
        }
        return "+1" + digits;
    }
}
