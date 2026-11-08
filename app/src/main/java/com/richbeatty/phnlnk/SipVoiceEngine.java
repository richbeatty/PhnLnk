package com.richbeatty.phnlnk;

import android.content.Context;
import android.util.Log;
import org.linphone.core.Account;
import org.linphone.core.AccountParams;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.RegistrationState;
import org.linphone.core.TransportType;

/**
 * Experimental, hardware-assisted cellular voice path:
 *
 * AT&T SIM phone -> standard Bluetooth HFP -> Asterisk chan_mobile on Linux
 * -> SIP -> liblinphone in the secondary Android device.
 *
 * This does not access protected Android call audio.
 * Everything is one local trusted LAN unless a secure network tunnel exists.
 */
final class SipVoiceEngine {
    private static volatile SipVoiceEngine current;
    private Core core;
    private VoiceCredentials.Data settings;
    private volatile String detail = "Not connected";
    private volatile String callDetail = "No voice call";
    private volatile boolean registered;
    private volatile boolean incoming;
    private volatile boolean inCall;

    private final CoreListenerStub events = new CoreListenerStub() {
        @Override public void onAccountRegistrationStateChanged(
                Core core, Account account, RegistrationState state, String message) {
            registered = state == RegistrationState.Ok;
            detail = registered ? "Voice bridge connected"
                    : "Registration: " + state + (message == null ? "" : " (" + message + ")");
            SipVoiceService.notifyUpdate();
        }

        @Override public void onCallStateChanged(
                Core core, Call call, Call.State state, String message) {
            incoming = state == Call.State.IncomingReceived;
            inCall = state == Call.State.Connected ||
                    state == Call.State.StreamsRunning ||
                    state == Call.State.Paused ||
                    state == Call.State.PausedByRemote;
            if (state == Call.State.End || state == Call.State.Error ||
                    state == Call.State.Released) {
                incoming = false;
                inCall = false;
            }
            if (incoming) callDetail = "Incoming voice call";
            else if (inCall) callDetail = "Voice call active";
            else callDetail = "Voice call: " + state;
            SipVoiceService.notifyUpdate();
        }
    };

    static synchronized SipVoiceEngine current() {return current;}

    static synchronized SipVoiceEngine create(Context ctx) throws Exception {
        if (current != null) return current;
        SipVoiceEngine result = new SipVoiceEngine();
        result.start(ctx.getApplicationContext());
        current = result;
        return result;
    }

    static synchronized void shutdown() {
        if (current != null) {
            current.stop();
            current = null;
        }
    }

    boolean isRegistered() {return registered;}
    boolean isIncoming() {return incoming;}
    boolean isInCall() {return inCall;}
    String status() {return detail;}
    String callStatus() {return callDetail;}

    private void start(Context context) throws Exception {
        settings = VoiceCredentials.load(context);
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
                context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new IllegalStateException("Microphone permission is needed for voice calls.");

        try {
            Factory factory = Factory.instance();
            core = factory.createCore(null, null, context);
            core.setAutoIterateEnabled(true);
            core.addListener(events);

            Address identity = factory.createAddress("sip:" + settings.user + "@" + settings.server);
            Address registrar = factory.createAddress("sip:" + settings.server + ":5060;transport=udp");
            if (identity == null || registrar == null)
                throw new IllegalStateException("Invalid private SIP gateway address");

            core.addAuthInfo(factory.createAuthInfo(
                    settings.user, null, settings.password, null, null, settings.server));
            AccountParams params = core.createAccountParams();
            if (params.setIdentityAddress(identity) != 0 ||
                    params.setServerAddress(registrar) != 0)
                throw new IllegalStateException("Could not create SIP account");
            params.setTransport(TransportType.Udp);
            params.setRegisterEnabled(true);
            params.setExpires(120);
            Account account = core.createAccount(params);
            core.addAccount(account);
            core.setDefaultAccount(account);
            core.start();
            detail = "Connecting to voice bridge...";
        } catch (Exception e) {
            stop();
            throw e;
        }
    }

    void dial(String raw) {
        if (!registered || core == null)
            throw new IllegalStateException("Voice bridge is not connected. Check the Linux bridge.");
        if (core.getCurrentCall() != null)
            throw new IllegalStateException("Finish the current voice call first.");
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.length() == 10) digits = "1" + digits;
        if (digits.length() != 11 || !digits.startsWith("1") ||
                digits.startsWith("1900") || digits.substring(1,4).charAt(0) == '0') {
            throw new IllegalArgumentException("Enter a valid US or Canadian phone number.");
        }
        if (digits.endsWith("911"))
            throw new IllegalArgumentException("Use the normal phone for emergencies.");
        Call created = core.invite("sip:" + digits + "@" + settings.server);
        if (created == null) throw new IllegalStateException("The SIP gateway refused the call.");
        callDetail = "Calling via Bluetooth voice bridge";
    }

    void answer() {
        if (core == null) return;
        Call call = core.getCurrentCall();
        if (call == null || call.getState() != Call.State.IncomingReceived)
            throw new IllegalStateException("No incoming voice call.");
        call.accept();
    }

    void hangup() {
        if (core == null) return;
        Call call = core.getCurrentCall();
        if (call != null) call.terminate();
    }

    void sendTone(char tone) {
        if ("0123456789*#".indexOf(tone) < 0)
            throw new IllegalArgumentException("Invalid keypad tone");
        if (core == null || core.getCurrentCall() == null)
            throw new IllegalStateException("No voice call active.");
        core.getCurrentCall().sendDtmf(tone);
    }

    private void stop() {
        registered = false;
        incoming = false;
        inCall = false;
        if (core != null) {
            try {
                Call active = core.getCurrentCall();
                if (active != null) active.terminate();
                core.removeListener(events);
                core.stop();
            } catch (Exception error) {
                Log.w("PhnLnk", "Voice bridge stopped with an error", error);
            }
            core = null;
        }
        detail = "Voice bridge off";
    }
}
