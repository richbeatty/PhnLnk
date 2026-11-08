# PhnLnk app-only cellular audio research

Updated November 7, 2026.

## Absolute product constraints

- Exactly two Android devices already owned by the user: the SIM host phone and secondary Android tablet/phone. PCs are an optional future client, **not a gateway**.
- Keep the SIM and existing AT&T number in the host phone.
- No additional Linux computer, Bluetooth receiver, ESP32, adapter, headset emulation hardware or secondary cellular service.
- No root and no separately installed Shizuku app.
- The main phone can stay home, powered on and on Wi-Fi.
- Incoming and outgoing calls must carry **both actual voice directions** to and from the secondary Android device.
- Pair once, recover after reboot, avoid waking the host screen through the app, and install updates without uninstalling.

## What has actually been demonstrated in open source

**JemRec** (Apache-2.0; https://github.com/jemcik/JemRec ) pairs an app with its own Android Wireless Debugging on loopback (127.0.0.1) using an embedded ADB client. It launches a shell-UID daemon, no external device, no root, no Shizuku. Its call recorder records downlink and uplink on tested phones. Its answering-machine implementation also plays a greeting into a cellular call on tested hardware, sending audio toward the caller with an AudioTrack directed to TYPE_TELEPHONY. This proves the outgoing audio direction *in at least one phone family*, not on the user's phone.

Key upstream files:
- `app/src/main/java/com/jemcik/jemrec/adb/AdbTransport.kt`
- `app/src/main/java/com/jemcik/jemrec/adb/AdbIdentity.kt`
- `app/src/main/java/com/jemcik/jemrec/setup/PairingNotification.kt`
- `shellserver/src/com/jemcik/jemrec/shell/GreetingPlayer.java`
- `shellserver/src/com/jemcik/jemrec/shell/AnswerMachine.java`
- Apache-2.0 notices needed if any code is adapted.

**Voice SIP Agent** (GPL-2.0-or-later; https://github.com/sparkcyf/android-voice-sip-agent ) proves an actual bidirectional cellular-to-SIP call bridge on several unrooted Android devices, using a shell-UID service currently started by Shizuku. The shell call-audio tap opens:
- `AudioManager.getCallDownlinkExtractionAudioRecord`
- `AudioManager.getCallUplinkInjectionAudioTrack`
- or hidden `AudioRecord.Builder.setCallRedirectionMode` / `AudioTrack.Builder.setCallRedirectionMode`
- or direct `AudioRecord`/ `AudioTrack` routing through TYPE_TELEPHONY.
Its `CallAudioTap.kt` bridges 20 ms PCM frames in both directions. Community reports include **Redmi Note 13 Pro MT6789** on Android 15; this is promising for a MediaTek-family host, but it does not establish MP01 compatibility.

Key upstream files:
- `privileged/src/main/kotlin/.../shell/CallAudioTap.kt`
- `privileged/src/main/kotlin/.../shell/AudioCapabilities.kt`
- `gateway/src/main/kotlin/.../MediaBridge.kt`
- `telephony/src/main/kotlin/.../GatewayInCallService.kt`
- `voice-api/src/main/kotlin/.../MediaPort.kt`
- GPL-2.0-or-later compliance is required if source is adapted.

**Critical inference:** JemRec demonstrates starting a persistent shell service from inside one ordinary Android app. Voice SIP Agent demonstrates bidirectional audio when a shell service has appropriate privileges. Replacing its external Shizuku launcher with a built-in loopback ADB bootstrapper is a reasonable **engineering hypothesis**, not a proven turnkey app.

## Architecture to build (no hardware)

```
Host phone (SIM stays here)
  1. PhnLnk UI (normal Android call controls, local pairing, connectivity)
  2. Embedded loopback ADB client (only on host, one-time system pairing)
  3. Shell-UID call-audio daemon (capture cellular downlink / inject uplink)
  4. Authenticated encrypted realtime media link
             |
             | Internet, encrypted, NAT traversal
             v
Second Android device
  1. PhnLnk dialer, contacts, recent calls and incoming notifications
  2. VoIP media engine for microphone + speaker
  3. Reconnect and call state sync
```

The host app must not represent successful pairing or visible telephony devices as proof of voice relay. A real cellular call must be audibly tested **both ways**.

## The unavoidable one-time setup

1. Enable Developer options and Wireless Debugging on the host Android phone.
2. Open the Android "Pair device with pairing code" dialog.
3. Type the pairing code into PhnLnk's persistent notification (because Android closes the dialog when the user leaves Settings).
4. PhnLnk uses its embedded ADB client with a persistently stored identity to connect to the phone **itself**, launch its daemon, then disconnect ADB.
5. If permitted, PhnLnk restarts the daemon automatically when a reboot or OS update stops it.

There is no separately installed Shizuku app. But this design **does use the shell privilege level that Shizuku would otherwise provide** and requires Android debugging. That fact must be shown plainly to the user. Wireless Debugging carries a security cost: it permits connections from explicitly paired keys. Pairings should be auditable and revoked when PhnLnk is disabled.

## Engineering milestones, in order

### Gate 1: Host MP01 feasibility

- [ ] Implement embedded ADB pairing, stable self-identity and reconnect; never open a broad unauthenticated local server.
- [ ] Build a small shell daemon; test shell identity and audio capabilities, no recording stored.
- [ ] Inspect TELEPHONY_RX and TELEPHONY_TX device exposure under the shell UID.
- [ ] In a known safe test call, capture live downlink for a few seconds, verify non-silence without persisting the conversation.
- [ ] Play an original generated test tone into the call uplink, while the remote test participant confirms it is audible, safely and with consent.
- [ ] Verify that both can run simultaneously. No usable full call bridge until ALL these pass.

### Gate 2: Media and call state

- [ ] Secure local daemon-to-app transport with mutual authentication.
- [ ] Capture 20 ms frames and transfer to the companion through an encrypted realtime link, with jitter buffering and echo avoidance.
- [ ] Capture companion microphone, inject into host uplink and verify actual conversation.
- [ ] Match call answering with media availability. Do not answer the incoming call remotely until the audio path is ready.
- [ ] In-call keypad DTMF, voice quality, call timer, busy/hold state and clear errors.

### Gate 3: Trustworthy daily use

- [ ] Persist host and client pairings until explicitly revoked, no recurring OTP login.
- [ ] Recover after reboot without Shizuku or manual ADB restart where Android permits; report downtime otherwise.
- [ ] Use properly encrypted transport, no exposed HTTP/5060/RTP ports; robust NAT traversal.
- [ ] Incoming notifications work on the companion with Android standby and battery restrictions.
- [ ] No host screen activity/wake locks from PhnLnk; vendor telephony itself may light the screen.
- [ ] Sign all subsequent APKs with one permanent signing certificate stored in GitHub Actions secrets. Previous temporary-debug APKs cannot upgrade over the certificate boundary.
- [ ] Test long calls, Wi-Fi disconnect/reconnect, missed call notifications, and multiple simultaneous client connections.
- [ ] Honest error state if shell permissions or audio routing stop working after firmware updates.

## When the approach is impossible on a given phone

If the host firmware refuses to provide the call downlink to the shell service, or its uplink hardware ignores a shell-created AudioTrack, the app **cannot** fix it by changing a UI permission. A privileged/system-integrated or OEM/carrier solution would then be necessary. Do not silently fall back to speakerphone acoustic coupling or claim that an untested workaround is complete.

## Scope correction

The v0.5.0 Bluetooth-to-Linux-SIP experiment remains an archived optional prototype but is **not an acceptable solution** under the user's no-additional-devices requirement. Do not build future PhnLnk releases that require a third hardware device.

No MP01 end-to-end test results exist yet.
