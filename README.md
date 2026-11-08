# PhnLnk

One Android app with two roles: a **host phone** that keeps its SIM and number, and a **companion Android device** that can control that phone's calls over a private network. The host also serves a simple browser interface for PCs.

[Download the newest Android APK](https://github.com/richbeatty/PhnLnk/releases)

## Getting started

**On the phone with your SIM:**

1. Install PhnLnk and choose **This phone has my SIM**.
2. Allow the phone permissions. The phone starts its local host service.
3. Tap **Pair a device**. A QR code and six-digit code appear.

**On a second Android device:**

1. Install the same APK and choose **Connect to my other phone**.
2. Scan the QR displayed on the host to open PhnLnk and pair automatically. If your camera doesn't open the app link, enter the Wi-Fi IP and six-digit code manually.
3. The app remembers the connection and can start incoming-call alerts with notification permission.
4. Use **Dial** for the full number pad or **Contacts** to find a contact stored on the second Android device.

**On a PC:**

1. Open the private Wi-Fi address displayed by the host in a browser.
2. Enter the six-digit code (you only do this once per browser).
3. Use the dial pad or choose a number from the local recent dial-request list.

The six-digit code expires after five minutes and works only once. Each paired device gets its own random token. The host can revoke all tokens from **Forget all paired devices**.

## Features in v0.3

- Same APK for a host or Wi-Fi-only Android tablet.
- Phone role selection without any manufacturer or carrier branding.
- Outgoing cellular call requests using the host's own SIM.
- Incoming ringing status, remote **Answer** and **End call** controls.
- Number pad on the Android companion and PC browser.
- Contacts search on the companion using *that device's own contacts*. Contacts are **not uploaded** to the host or any cloud.
- Favorites and last 20 dial attempts stored on the companion device.
- Optional foreground monitoring on the companion that notifies when a call starts ringing on the host. Tapping the alert opens the call-control screen.
- Private LAN pairing by QR code or six-digit one-time code.
- Pairing stored on the device so it does not need to be repeated each time.
- No Shizuku, rooting or carrier changes required **for call control**.

## Major limitation

**This is not yet an all-in-one remote phone.** The host can initiate/answer/end cellular calls, but Android's ordinary app APIs do **not** allow the remote companion's microphone and speaker to carry the live cellular audio. When you answer through PhnLnk, the conversation still happens through the host phone.

That means actual remote conversations, caller ID for incoming calls, in-call touch tones, and remote access away from home are **not implemented**. PhnLnk is a call-control preview, not a Beeper equivalent or a replacement for a phone.

## Security

The host runs an HTTP server on port 47321. Requests have authorization tokens, but network traffic **is not encrypted**. Only connect while on a private, trusted Wi-Fi. Do not forward the HTTP port or expose it to the public internet. Do not use public Wi-Fi or put sensitive call or contact information through this prototype. Contacts stay local on the secondary Android device.

Incoming alerts require notification permission and an active foreground monitoring service. Background monitoring is experimental and depends on Android battery restrictions.

The dialer is limited to ordinary US/Canadian 10-digit numbers (and +1 numbers) and blocks 900 numbers. Use your normal phone for emergencies.

## Installation notes

Early APK releases have been signed with GitHub Actions' temporary debug signing keys. Android may require you to **uninstall the earlier preview before installing this one**. Uninstalling resets the saved pairings on that device.

The project needs a stable, securely stored signing key for future seamless Android upgrades.

## Build

JDK 17, Android SDK 35 and Gradle 8.9:

```sh
gradle :app:assembleDebug
```

The Android build workflow creates and attaches `PhnLnk-Android.apk` to the matching GitHub release.

## Roadmap

1. Verify host device call control and notifications on multiple models.
2. Stable APK signing and direct in-app updates.
3. Improved local device discovery and secure, encrypted pairing.
4. Incoming caller identification (subject to Android platform restrictions).
5. Secure remote reachability beyond one Wi-Fi network.
6. Investigate **genuine two-way cellular call audio** without privileged helpers. This remains the blocking capability and must not be claimed until proven on supported devices.
7. Call log, contact names, and other conveniences where permissions permit.

## License

The original code is MIT. QR encoding uses ZXing core under Apache-2.0. No GPL cellular-audio gateway code is included.
