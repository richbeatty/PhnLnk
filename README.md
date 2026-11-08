# PhnLnk

**One Android app, two roles.** The main phone keeps its cellular SIM and number. A secondary Android device can dial, browse local contacts, send keypad tones, receive incoming-call alerts, and control calls.

[Download call-control preview v0.4](https://github.com/richbeatty/PhnLnk## App-only audio research: no third device

**Product requirement:** The finished app must use only the existing SIM phone and the existing second Android device. No Linux bridge, extra computer, headset gateway, dongle, root, or separately installed Shizuku.

The credible, still unproven route is an embedded loopback ADB client, inspired by [JemRec](https://github.com/jemcik/JemRec), which launches a shell-level helper from the phone itself after one-time Wireless Debugging pairing. [Voice SIP Agent](https://github.com/sparkcyf/android-voice-sip-agent) proves a two-way audio bridge at the same shell privilege level but currently uses Shizuku to start it. Integrating these techniques into one PhnLnk app would avoid a separate Shizuku installation, though a one-time Developer options and Wireless Debugging setup is still required.

**Both recording the live cellular downlink and injecting audio into the cellular uplink must be confirmed on the actual host phone** before treating the approach as viable. MediaTek/Android support varies by device and firmware. This has not been verified on the user's phone.

[Read the detailed app-only research and testing plan](docs/APP_ONLY_AUDIO_PLAN.md).

## Historical experimental Bluetooth/SIP branch (not the requested solution)

The v0.5.0 APK contains a separate Linux Bluetooth-to-SIP experiment. It requires another computer and is **not a solution under the no-additional-hardware requirement**. The v0.5 voice path remains untested and is not recommended for this use case.

## Basic pairing

On the phone with your SIM, choose **This phone has my SIM**, grant phone permissions, then **Pair a device**. On the secondary Android device, install the same PhnLnk app and choose **Connect to my other phone**, then scan the QR code or enter the displayed local Wi-Fi IP and six digits. Pairing is remembered until revoked. Desktop browsers can use the URL on the host phone.

PhnLnk works on Wi-Fi-only Android tablets too. All UI is device-neutral.

## Current features

- Native Android dial pad, contacts, favorites and last call requests.
- Desktop browser dial pad and saved pairing.
- Dial, answer, hang up through the SIM phone over a trusted private LAN.
- In-call DTMF after selecting PhnLnk as the host phone's default dialer.
- Incoming call notifications, pairing recovery and user-controlled host background service.
- Experimental Bluetooth/SIP components exist in v0.5 but require additional hardware. They are out of scope for the app-only target.
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
