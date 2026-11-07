# PhnLnk

Use an Android phone as a host for your existing cellular number while you browse on another device.

**Target:** Minimal Phone MP01 on AT&T, Android 14. Keep the existing carrier and number.

## Download

**[Open Releases to download the APK](https://github.com/richbeatty/PhnLnk/releases)**

The current release is an **early call-control prototype**, not yet a remote calling solution. On your MP01 it can request an outgoing cellular call, answer a ringing call, and end a call from a browser on the same trusted network.

**Two-way audio does not travel to the tablet or PC yet.** Audio stays on the MP01, so you cannot have a remote conversation with this release. The Android app itself does **not** require Shizuku or root.

## Install

1. From **Releases**, choose the latest PhnLnk preview and download `PhnLnk-MP01.apk` to the MP01.
2. Open the APK and allow installation from that source, if Android prompts.
3. Open PhnLnk and grant its phone permissions.
4. Tap **Start host**.
5. On a tablet or PC on the same trusted Wi-Fi, visit the address displayed on your MP01 (usually `http://192.168.x.x:47321`).
6. At the browser sign-in prompt, enter username `phnlnk` and the **pairing key** shown in the MP01 app.

Do not forward TCP port 47321 to the public internet. The browser prototype uses HTTP Basic authentication, which is **not encrypted on the local network**. Use only on a trusted private network; do not use it over untrusted Wi-Fi. A future release needs encrypted pairing, a native tablet client, and a secure relay.

## Why can't I just call from the browser?

Android deliberately blocks third-party apps from capturing or injecting audio on an active cellular call. The permission `CAPTURE_AUDIO_OUTPUT` is reserved for privileged/system applications. A regular installed APK cannot obtain it. This is the missing part, not the user interface.

There are experimental no-root gateways based on Shizuku and hidden telephony APIs, including [Voice SIP Agent](https://github.com/sparkcyf/android-voice-sip-agent), but they are not plug-and-play and **have not been tested on the MP01**. We will not silently require Shizuku or claim the audio works.

## Project status

- [x] Android host for MP01 with regular Android call-control APIs
- [x] Browser dialer for tablet/PC
- [x] Device-generated pairing key
- [x] GitHub Actions APK build and downloadable preview release
- [ ] Secure network transport and streamlined pairing
- [ ] Incoming caller ID and push notifications
- [ ] Native Android tablet client
- [ ] Two-way cellular call audio remotely
- [ ] MP01 device validation and unattended calling
- [ ] Usable away from home without managing a VPN

The important feature is the two-way audio. Other checkboxes do not make this a finished calling app.

## Build yourself

JDK 17, Android SDK Platform 35, and Gradle 8.9:

```sh
gradle :app:assembleDebug
```

Build output: `app/build/outputs/apk/debug/app-debug.apk`.

## Security and safety

The host is off until manually started, displays a foreground notification, and can be stopped from the MP01. The built-in dialer refuses emergency numbers and North American 900 numbers. Never depend on this experimental interface for emergency calling. The APK does not request SMS access, contacts, or audio recording.

## License

MIT applies only to PhnLnk's original code. Incorporating GPL audio gateway code would require complying with the gateway's license.
