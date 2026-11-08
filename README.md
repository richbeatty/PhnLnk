# PhnLnk

Keep the SIM in your main Android phone and use a second Android device to control calls. The long-term goal is real incoming and outgoing cellular calls with two-way audio on the companion, using only the **two Android devices you already own**, no Shizuku installation, root, computer, Bluetooth adapter, or secondary carrier account.

## Download

[PhnLnk Android releases](https://github.com/richbeatty/PhnLnk/releases)

### Version 0.6.0: app-only audio compatibility test

This release implements a significant first step toward an app-only voice relay:

- Built-in Android Wireless Debugging pairing (no Shizuku or computer).
- One-time system pairing via a notification while the Android Settings pairing dialog remains open.
- An Android-shell audio helper bundled with PhnLnk, built with D8.
- Local-only helper that authenticates with a random per-installation key and never listens on the Wi-Fi network.
- Live cellular voice capture measurement, with no recording saved.
- User-approved short and quiet audio tone routed toward the cellular uplink, so the other caller can confirm whether it was audible.
- Existing native Android dialer, contacts, favorites, in-call keypad support and one-time companion pairing.

**This release does not yet carry live voice to the second Android device.** Its purpose is to establish whether both audio directions are actually accessible on your specific host phone. A successful Android build does not establish hardware or firmware compatibility.

### Test on the main phone

1. Install the APK and open PhnLnk. Choose **This phone has my SIM** if asked.
2. Open **Set up and test call audio**.
3. Open Android **Developer options** and enable USB debugging and Wireless debugging (on trusted home Wi-Fi).
4. In PhnLnk choose **Show pairing notification**. Then in Android Wireless debugging tap **Pair device with pairing code**.
5. Keep Android's six-digit pairing dialog open, swipe down the notification shade, and enter the code into PhnLnk's pairing notification. The app saves its own unique ADB identity for later sessions.
6. Return to PhnLnk and choose **Start local audio test helper**. The result must say `READY:V1:shell UID 2000`.
7. During an ordinary test call with a consenting person, press **Test receiving caller audio**. Ask the person to speak during the test. A nonzero RMS signal is useful evidence but is not by itself proof of the downlink source.
8. While that person knows you will send a quiet beep, press **Send short test tone into call**. Ask if they actually heard it. Android accepting a routed AudioTrack does not guarantee audibility.

**Please note the exact results from both tests.** Only real downlink capture and audible uplink injection justify building the final realtime streaming part.

### Current everyday controls

- SIM phone: hosts your number, can dial, answer, end a call and optionally send in-call menu tones as the default Phone app.
- Second Android device: dial pad, local contacts, favorites, recent requests, incoming-call alerts and remembered pairing.
- PC browser: dial pad and authenticated call controls on a trusted local network.

These controls still **do not send voice audio** to the companion. Do not rely on them as a telephone replacement. Never use PhnLnk for emergency calls.

## Security

Enabling Android debugging is a significant permission change. PhnLnk's self-pairing uses the Android **shell** identity, not ordinary app privileges. This is needed because Android denies cellular audio access to ordinary third-party apps. Any ADB key trusted by your phone can run shell commands. Review and revoke authorized debugging keys from Android settings if you stop using the feature. Keep your phone on trusted Wi-Fi and use a secure screen lock.

The test helper binds to **127.0.0.1 only**, requires a random 256-bit per-install secret, and contains no remote shell command interface. The capture test calculates levels only and does not write call audio to storage. Injection plays only a quiet short test tone after a confirmation dialog. The normal PhnLnk browser call-control server is still HTTP on local network port 47321 and must never be exposed to the public internet.

The helper is **not silently started or given audio access**. Press Start in the host UI, then separately approve each live-call test. Pairing is remembered, but shell helper restarts after reboot may still require Wireless Debugging access. Android's OS-level call UI may wake the host phone; PhnLnk does not force it to wake.

## Release signing

To allow future APKs to install over the previous ones, configure a permanent private PKCS12 signing key using encrypted GitHub Actions secrets `PHNLNK_KEYSTORE_B64` and `PHNLNK_STORE_PASSWORD`, alias `phnlnk`. Until that is done, GitHub Actions previews use temporary debug keys; Android can refuse update installs and require one uninstall. Do not commit the keystore to a public repo.

## Source and build

Requires Java 17, Android SDK 35, Gradle 8.9, and the SDK D8 build tool. The CI workflow compiles `shellprobe/src` to an isolated DEX asset before building the Android APK.

```sh
bash shellprobe/build.sh
gradle --no-daemon :app:assembleDebug
```

[Technical app-only research](docs/APP_ONLY_AUDIO_PLAN.md) | [Audio test plan](docs/AUDIO_TESTING.md)

PhnLnk's original code is MIT. The embedded ADB library from MuntashirAkon is available under Apache-2.0 or GPL-3.0-or-later. Its use here is under Apache-2.0. Bouncy Castle, Conscrypt, and ZXing have their own notices. The archived Bluetooth SIP experiment is no longer part of the APK; see the historical research only.
