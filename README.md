# PhnLnk

A self-hosted experiment for making an existing Android cellular line usable from a tablet or PC, without moving the number or switching carriers.

**Target:** Minimal Phone MP01, Android 14, AT&T. The MP01 stays powered on and connected to Wi-Fi at home.

## Status

**v0.1 is a call-control and compatibility prototype, not a voice relay.** The Android host app runs a small authenticated HTTP server and provides a browser dialer. It can request outgoing calls, answer ringing calls, and end calls using public Android APIs where the device permits them. Call audio **still comes from the MP01 itself**. Incoming caller ID and two-way remote audio are not implemented.

This distinction is intentional: the real challenge is capturing and injecting the cellular call's audio on this particular phone.

## First test

1. From the **Actions** tab, run **Android debug APK** (or use the artifact from the latest successful build).
2. Install the APK on the MP01.
3. Open PhnLnk, grant phone permissions, and select **Start host**.
4. Find the host's local address in the app. On a trusted home network, visit `http://ADDRESS:47321` from your tablet or PC.
5. When prompted to sign in, use username `phnlnk` and the pairing key displayed by the host app.
6. The browser displays call status, a dialer, answer, and hang-up controls. Actual voice stays on the phone for this build.

For remote access away from home, use a private VPN such as Tailscale on the host and client. **Never forward port 47321 to the public internet.** This prototype uses HTTP Basic authentication, not HTTPS. Do not use it on an untrusted Wi-Fi network.

## MP01 audio compatibility test

On a computer with ADB and the MP01 connected, run:

```sh
adb shell getprop ro.build.version.release
adb shell getprop ro.product.cpu.abi
adb shell 'dumpsys media.audio_policy | grep -E "TELEPHONY_TX|TELEPHONY_RX"'
```

Seeing both audio devices makes privileged interception worth testing, but does **not** prove that two-way voice audio works. A real cellular call with a Shizuku-based gateway is needed.

## Next development milestone

Connect the browser/Android client to a real two-way audio gateway. The strongest existing foundation is [sparkcyf/android-voice-sip-agent](https://github.com/sparkcyf/android-voice-sip-agent), which reports successful call bridging on several unrooted Android devices using Shizuku and SIP. It is **not verified on the MP01**. The [self-host fork](https://github.com/Hinduc0der/voice-sip-agent-selfhost) includes a SIP registrar but does not yet implement outgoing remote calls. See [research notes](docs/RESEARCH.md).

Android blocks ordinary apps from directly recording/injecting cellular-call audio. We will not represent call control as voice bridging until actual two-way audio is tested.

## Build

Requirements: JDK 17, Android SDK Platform 35, Gradle 8.9 (or use the GitHub Actions build).

```sh
gradle :app:assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Safety and privacy

* No SMS permissions or message access.
* No microphone, contact, or call-recording permissions in this prototype.
* A random pairing key is generated on-device and is not sent to any cloud service.
* The server only runs when you start it, shows a persistent notification, and can be stopped in the app.
* Remote dialing is restricted to North American 10-digit numbers or +1 numbers, with premium-rate area code 900 blocked; requests are authenticated.
* Not for emergency calling. Emergency numbers are refused by the web dialer. Use the regular phone or emergency services directly.
* Keep Android and AT&T calls available on the MP01 while testing. Do not rely on experimental calling for critical communication.

## License

MIT for PhnLnk's original code. If GPL-licensed gateway code is incorporated into this app later, redistribution must honor that project's GPL license and related notices.
