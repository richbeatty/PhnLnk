# PhnLnk

PhnLnk is an experimental Android host for controlling calls on the Minimal Phone MP01 through a tablet or PC. It keeps AT&T and the existing phone number.

## Download and connect

[Download PhnLnk for MP01](https://github.com/richbeatty/PhnLnk/releases/latest)

1. Install PhnLnk on the MP01 and allow phone permissions.
2. Tap **Pair a device** on the MP01.
3. On an Android tablet, **scan the QR code** shown on the MP01. It should open the browser and pair automatically.
4. On a PC, open the address shown on the MP01 and enter the **six-digit code**.
5. The browser remembers the pairing. No usernames, no long passwords, and no repeated login.

The six-digit code expires after 5 minutes, is one-time use, and stops accepting attempts after five failures. Paired browsers receive independent long random keys. The MP01 stores hashes of the keys. On the MP01, **Forget all paired devices** revokes access.

The pairing changes replace the long Basic authentication login in v0.1.1. Browsers must pair again once. Because these early APK builds were signed using temporary GitHub Actions debug keys, Android may require uninstalling v0.1.1 before installing v0.2.0.

## What it does and does not do

You can ask the MP01 to dial, answer and end cellular calls from a browser on the same private network. **Remote call audio still does not work.** Your tablet or PC cannot carry the actual conversation. The MP01 has not been verified to support cellular audio capture and injection without special privileges. This release does not require Shizuku or root.

The built-in web server uses ordinary HTTP, including for the pairing token. **It is not encrypted and is not safe to expose to the public internet or untrusted networks.** Use a trusted, private Wi-Fi network only. The host is not remotely reachable outside the local network without separately configured private networking.

This is a development preview, not a Beeper equivalent or an emergency-calling service. Use the MP01 directly for emergency calls.

## Build

Requires JDK 17, Android SDK 35, and Gradle 8.9:

```sh
gradle :app:assembleDebug
```

## License

PhnLnk original code: MIT. QR encoder: ZXing core, Apache-2.0. No third-party telephony gateway code is included.
