# PhnLnk 0.6 host audio test protocol

**Purpose:** Establish host-phone compatibility before designing any live remote audio relay. No third device, Shizuku app or root required for the diagnostic. The only new system feature is Android's own Wireless Debugging, explicitly authorized by the phone owner.

## Prerequisite

- Host Android phone on a trusted Wi-Fi network.
- Android Developer options, USB debugging and Wireless debugging switched on.
- One-time local ADB pairing in the PhnLnk notification using Android's own six-digit code.
- Caller participating in a test call has agreed to both the short capture measurement and optional test beep.

## Checks and interpretation

| Test | Success looks like | What it does NOT prove |
|---|---|---|
| Launch helper | `READY:V1:shell UID 2000` | Access to call audio |
| Receive caller audio | `CAPTURE:Samples=...,RMS=...,Peak=...,SIGNAL_DETECTED` during caller speech | Voice on far end rather than local room/source mix |
| Send beep to far end | `INJECT:Output routed to TYPE_TELEPHONY`, **and the consenting far-end caller confirms hearing it** | Simultaneous full-duplex operation or stable device routing |

Repeat the two audio tests while the screen is off and after one reboot to assess reliability. A sampling error or silence may be caused by firmware, Wi-Fi calling, cellular mode, permissions, audio route or a dormant caller.

The probe uses a 16 kHz mono short-lived AudioRecord source `VOICE_CALL` under Android's shell UID and an AudioTrack directed toward `TYPE_TELEPHONY`. Other hidden audio backends known from Voice SIP Agent are **not** yet implemented. If this simple route fails, do not infer that every possible backend fails.

## Handling test outcomes

- If either direction cannot be confirmed, do not enable production voice relay; record the error and inspect the audio route from the host only.
- If both directions succeed independently, the next test is simultaneous full duplex, without saving call audio.
- Only after that should PhnLnk build an authenticated, encrypted, low-latency Android-to-Android audio stream, with mobile network reachability and call lifecycle recovery.
- Never use the test tone on an emergency call, busy conference line, or without the caller's awareness.

## Security

- Test daemon binds to loopback 127.0.0.1:47327. It refuses to run unless UID is 2000 (shell).
- One random 256-bit key per PhnLnk install; only three daemon commands are supported.
- No call recordings or PCM audio are persisted or uploaded during diagnostics.
- ADB pairing lasts until revoked and requires the owner's explicit Android Settings approval. Keep only trusted ADB pairings.
- Do not expose PhnLnk's separate HTTP call-control port 47321 or adb debug ports to the public Internet.

## Compatibility results

**None yet.** Android compilation and GitHub release success cannot establish call capture or uplink injection compatibility on the user's actual phone.
