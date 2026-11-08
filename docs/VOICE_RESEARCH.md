# Voice gateway research, November 2026

## Question

Can an ordinary, unrooted Android 14 phone share the actual audio of a carrier cellular call with another Android tablet, using only two installed apps, no Shizuku and no carrier change?

**Answer based on Android's current documented interfaces: no verified generic solution.** Android allows regular apps to dial and manage calls via Telecom. It does not provide an ordinary-app API for the protected mobile uplink and downlink audio. Holding the default Phone role permits InCallService and DTMF, not raw modem audio capture or insertion.

Authoritative references:
- Android audio input sharing: https://developer.android.com/media/platform/sharing-audio-input
- Audio playback capture restrictions: https://developer.android.com/reference/android/media/AudioPlaybackCaptureConfiguration
- AOSP call-audio interception permission: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/core/res/AndroidManifest.xml
- Android companion app streaming reserved to preinstalled privileged components: https://source.android.com/docs/core/permissions/app-streaming

## Paths evaluated

| Route | Actual voice both ways? | Root/Shizuku? | Extra device/service? | Verdict |
| --- | --- | --- | --- | --- |
| Android Telecom InCallService | No, control and DTMF only | No | No | Keep as fallback |
| MediaProjection playback capture | No, cellular usage excluded | No | No | Not viable |
| Record voice uplink/downlink | Privileged access only | Yes, without OEM provisioning | No | Not viable for ordinary APK |
| Android 14 CallStreamingService | For supported VoIP/system roles, not third-party cellular access | OEM/privileged | No | Not available generally |
| [Voice SIP Agent](https://github.com/sparkcyf/android-voice-sip-agent) | Yes, device dependent, tested | Shizuku | SIP PBX | Excluded by requirements |
| [Voice SIP Agent Selfhost](https://github.com/Hinduc0der/voice-sip-agent-selfhost) | Incoming yes, device dependent | Shizuku | none for SIP server | Excluded by requirements |
| [GSM2SIP](https://github.com/pulpoff/gsm2sip) | Yes on supported devices | Root / Magisk | SIP server | Excluded |
| [P2P Phone Call Gateway](https://github.com/neyro-droid/P2P-PHONE-Call-Gateway) no-root mode | Acoustic mic/speaker workaround | No | PC relay | Echo, silent mic risk, not credible as clean voice |
| AT&T NumberSync | Yes on explicitly supported devices | No | Carrier-compatible hardware/account | Not a generic third-party app API |
| [CallScoot](https://github.com/efekurucay/callscoot) | Yes via standard Bluetooth HFP phone to Linux audio | No | Nearby Linux computer | Strong open HFP base |
| [Bluetooth GSM SIP gateway](https://github.com/k-chatz/bluetooth-gsm-sip-gateway) | Yes, incoming/outgoing via HFP -> Asterisk -> SIP | No Android root/Shizuku | Nearby Linux computer | **Most direct documented end-to-end path** |
| [ESP32 Bluetooth Voice Agent](https://github.com/michaellee8/esp32-bluetooth-voice-agent) | Bluetooth HFP -> WebRTC two-way audio demonstrated | No Android root/Shizuku | ESP32 and computer bridge | Promising dedicated hardware, experimental |
| [FOX-1 call bridge](https://github.com/ikequan/fox-1-call-bridge) | SCO via ESP32 and Bluetooth SPP | No Android root/Shizuku | ESP32 | Promising, app integration specific |

## Selected architecture

```
AT&T SIM phone (stock Android)
    | standard Bluetooth HFP call audio, bidirectional
    v
Nearby Linux Bluetooth audio gateway
    | Asterisk chan_mobile / SIP media
    v
PhnLnk Android companion
    | liblinphone microphone + speaker, real two-way voice
    v
User
```

The host phone continues to own its cellular number, carrier, and actual cellular call. No forwarding, porting or second cellular plan is required. The Bluetooth-to-SIP gateway is necessary because an ordinary Android APK cannot access the protected call-audio path.

## Practical limitations and cautions

1. Bluetooth gateway **must be within Bluetooth range of the host**, turned on and paired. A Linux mini PC with Bluetooth can fill the role. This is not app-only.
2. The upstream k-chatz Asterisk container uses Ubuntu 18.04 (end-of-life), a privileged container and default sample password. Treat it as a proof of concept, not a secured public production service.
3. Start only on a trusted home LAN. Do **not** publicly expose SIP/RTP or the original PhnLnk HTTP control server. Remote calling needs a secure private overlay or properly secured SIP/TLS and SRTP with maintained endpoints.
4. The host-phone Asterisk Bluetooth channel may require per-phone tuning. No evidence validates the Minimal Phone hardware's Bluetooth call route yet.
5. The Linphone SDK is AGPLv3 licensed. Compiled versions incorporating it need to comply with AGPLv3 and make the combined source available to recipients.
6. Without a verified Linux Bluetooth bridge, the optional SIP client alone cannot carry a cellular call. PhnLnk call control remains available.
7. SIP registration while Android is backgrounded requires foreground-service microphone permission and may be limited by OS battery policy. Test wake, reboot and long calls.
8. Never route emergency calls through the experiment. Test with a separate known safe nonemergency number.

## Other reference architecture

For a smaller dedicated hardware accessory, an original ESP32 supporting Bluetooth Classic HFP can act as the headset, with a Wi-Fi or WebRTC bridge. Espressif maintains an official HFP client API:
https://docs.espressif.com/projects/esp-idf/en/stable/esp32/api-reference/bluetooth/esp_hf_client.html

This accessory could eventually yield a more turnkey one-time pairing process than a Linux mini PC, but would require genuine hardware engineering and repeatable audio quality validation.

## Test plan for the first real-voice milestone

- Pair the host Android phone to a Linux Bluetooth adapter as a headset/car audio device.
- Verify incoming and outgoing cellular calls route both ways to a local Linux audio endpoint using PipeWire/CallScoot or Asterisk chan_mobile.
- Verify the Asterisk SIP extension registers and completes a call with a known SIP softphone.
- Connect PhnLnk's embedded Linphone SIP voice mode using the same extension settings.
- Verify caller hears voice from the tablet microphone and tablet hears caller, using separate people/devices and a controlled test.
- Validate contact dialing, DTMF, call duration, Wi-Fi reconnect and accidental disconnected call state.
- Only then describe the optional Bluetooth mode as working on that particular combination.

No tested MP01 end-to-end Bluetooth audio results are claimed in this repository.
