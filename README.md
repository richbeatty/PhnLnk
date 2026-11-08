# PhnLnk

**One Android app, two roles.** The main phone keeps its cellular SIM and number. A secondary Android device can dial, browse local contacts, send keypad tones, receive incoming-call alerts, and control calls.

[Download PhnLnk](https://github.com/richbeatty/PhnLnk/releases/latest)

## The cellular voice breakthrough, optional beta

An ordinary Android app cannot capture and inject native carrier call audio through public APIs. **PhnLnk 0.5 adds an experimental real-voice route through a standard Bluetooth hands-free gateway**, the same route used by car kits. It does not require rooting your Android phone or installing Shizuku.

```
Main Android phone with SIM and original cellular number
  -> Bluetooth HFP (standard call-audio device)
  -> nearby Linux computer with Bluetooth and Asterisk chan_mobile
  -> SIP over your private network
  -> PhnLnk embedded Liblinphone on secondary Android device
  -> its own microphone and speaker
```

**A Linux Bluetooth/SIP gateway is required and must be set up separately.** The extra computer must stay near the main phone. The solution has not been tested end-to-end on the user's hardware and is not yet a polished, app-only Beeper alternative. The voice engine requires SIP registration before making calls. If it is off, PhnLnk falls back to its current control-only functions, which do not carry audio.

See [Bluetooth voice setup](docs/VOICE_SETUP.md) and [extensive research](docs/VOICE_RESEARCH.md).

## Basic pairing

On the phone with your SIM, choose **This phone has my SIM**, grant phone permissions, then **Pair a device**. On the secondary Android device, install the same PhnLnk app and choose **Connect to my other phone**, then scan the QR code or enter the displayed local Wi-Fi IP and six digits. Pairing is remembered until revoked. Desktop browsers can use the URL on the host phone.

PhnLnk works on Wi-Fi-only Android tablets too. All UI is device-neutral.

## Current features

- Native Android dial pad, contacts, favorites and last call requests.
- Desktop browser dial pad and saved pairing.
- Dial, answer, hang up through the SIM phone over a trusted private LAN.
- In-call DTMF after selecting PhnLnk as the host phone's default dialer.
- Incoming call notifications, pairing recovery and user-controlled host background service.
- Optional **Bluetooth voice calling** with a paired Linux-to-SIP gateway. When registered, the companion's dial pad and contacts use SIP, and real two-way voice uses its microphone/speaker through Liblinphone.
- Voice gateway SIP credentials encrypted with a key kept in Android Keystore.
- No Shizuku and no root needed on the Android devices.

## Limitations and safety

- PhnLnk **cannot relay cellular audio directly between two stock Android phones**. The Bluetooth/SIP hardware route is optional and requires a nearby Linux computer.
- Ordinary call control uses unencrypted HTTP on the local network. SIP prototype uses UDP/RTP on a private LAN. **Never expose either port to the public internet.** For off-LAN use, you would need a secure private network or properly hardened SIP/TLS and SRTP infrastructure.
- Bluetooth hardware, SIP registration, voice quality, host wake behavior, background delivery, and call routing must all be verified on the user's devices before production use.
- Voice service stays online using an opt-in microphone foreground service. Android can stop services after force-stop or battery management; reopening PhnLnk retries.
- It is not an emergency calling service.
- A generic Android app cannot override the system's choice to wake the main phone for incoming cellular calls.

## Update signing

GitHub Actions can produce consistently signed releases if `PHNLNK_KEYSTORE_B64` and `PHNLNK_STORE_PASSWORD` repository secrets are configured with a permanent private signing key (PKCS12 alias `phnlnk`). Until then, preview builds use ephemeral debug keys and may require reinstalling rather than updating in place. **Never place signing keys in a public repo.** A previous debug-signed app cannot be updated by a differently signed APK.

## Build and licenses

Java 17, Android SDK 35, Gradle 8.9. Liblinphone's official Maven repository is included in settings.gradle.

```sh
gradle :app:assembleDebug
```

PhnLnk's original code is MIT-licensed. The optional integrated Liblinphone SIP engine is AGPLv3, so distribution of the **combined application** is subject to AGPLv3 terms and source availability. See [third-party notices](docs/THIRD_PARTY.md) and the project's public source.

This is an experimental personal app project, not an audited production telecom product.
