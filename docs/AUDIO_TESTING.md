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

## v0.9.0: end-to-end engineering test

This version includes real PCM streaming between the host and companion. It is a **compatibility experiment**, not a certified calling product.

1. On the MP01, upgrade without uninstalling. Open Guided audio setup. If the saved V1 shell probe remains, retry so PhnLnk installs the new V2 shell bridge.
2. Make a real cellular call to a consenting person. Ask them to speak steadily while tapping Check incoming call sound. The app reports REDIRECT, DOWNLINK and/or CALL source RMS and peak. A method reporting nonzero levels is a candidate, not proof it is the far-end caller.
3. Tap Send quiet audio test tone, then ask whether the far-end person heard it. Try REDIRECT and TELEPHONY uplink modes separately if necessary.
4. In the companion, with the call active and both devices on the same private Wi-Fi, tap Start voice on this device. Approve its microphone foreground permission. Speak in both directions and confirm that each person hears the other. Use mute and speaker route controls.
5. If no sound or one-sided audio, use the host's manual backend selectors to isolate the working route. Document device model, OS build, backends, signal levels and whether the remote person actually heard the test tone.
6. Check disconnect, re-answer, call end, screen-off, reboot and Wi-Fi reconnection. Keep Android's stock Phone app for emergencies.
7. For away-from-home tests, set up an independently provisioned encrypted private VPN on both phones. No port forwarding. Do not assume the VPN/connection is provided by PhnLnk.

The voice channel on TCP 47322 is authenticated and AES-256-GCM encrypted. The older HTTP call-control connection on TCP 47321 is not encrypted and must never be exposed publicly.

**Never claim the full-duplex cellular feature works until step 4 is successfully tested on the user's physical phone.** A successful compilation or a "voice stream connected" state alone cannot establish downlink, uplink, or carrier compatibility.
