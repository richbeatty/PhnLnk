# Validation and acceptance plan

The user does not want another speculative install cycle. The objective is to catch every software-detectable problem before recommending a device test, then be explicit that hardware behavior cannot be proven without a device test.

## Test levels

| Level | Execution | What it can establish | What it cannot establish |
| --- | --- | --- | --- |
| Source invariants | `python3 tools/maintainer_check.py` | Required code paths, permission declarations, product contract | Runtime behavior |
| Protocol JVM tests | `bash tools/test_voice_protocol.sh` | Actual production Java handshake and AES frame logic over loopback sockets | Android framework, cellular audio |
| Android compilation | GitHub Actions `assembleDebug` | Source compiles against Android SDK and dependencies | Device installation or call routing |
| Shell DEX compilation | `bash shellprobe/build.sh` | Helper bytecode is generated and bundled | Shell UID privilege or audio policy |
| Release compilation | GitHub Actions `assembleRelease` | Android release variant builds | APK signing or voice audibility |
| Signed APK verification | Android `apksigner verify` and ZIP inspection | Integrity and signing certificate match | Installation or real calls |
| Main phone compatibility | Physical MP01 with consenting caller | Whether actual cellular speech reaches capture and injection routes | All other vendors or network conditions |
| End-to-end acceptance | Both physical Android devices and live call | Audible bidirectional phone conversation and call controls | Every background, carrier and roaming scenario |

## Current automated voice tests

The JVM harness in `tools/voice-tests/` compiles the exact checked-in `VoiceSecureTransport.java` file with only three small Android dependency stubs. It opens actual loopback TCP sockets.

Cases:

1. Companion and host complete HMAC authentication and exchange 640-byte encrypted frames in both directions.
2. Server closes before the initial magic: client raises a named early handshake EOF.
3. Server closes after receiving the client proof but before the server proof: client names the authorization phase.
4. Host and companion token fingerprints differ: authentication is refused.
5. Server closes before a full media frame: client raises `VOICE_MEDIA_CLOSED` instead of a raw EOFException.
6. Tampered AES-GCM data is rejected.
7. A duplicated sequence number is rejected after a valid frame.

The harness does not generate a cellular call or synthetic claim of caller audibility. The tests are added to the GitHub workflow before Android compilation.

## Integration regressions to catch

- Old V1 helper is replaced by V2 on a version upgrade without clearing the stored Android debugging identity.
- An exception before STREAM_READY causes the helper to send an explicit error if the socket is still usable.
- No UTF error message is inserted after binary PCM streaming begins.
- Host reports which stage failed and exposes sanitized details to an authenticated paired companion.
- Audio start-up budgets allow backend initialization before steady-state read timeout.
- A dropped host TCP connection does not leave the companion's microphone active indefinitely.
- Competing voice clients do not both become active.
- Pairing revocation ends media.
- The app's normal dialer view can be opened, switched to Contacts and back without parenting the same View twice.
- The user can disable optional debugging reconnect and the app does not silently turn Android debugging on.

Some of these require Android emulator or instrumented tests that have **not yet been built**. Maintain them as open tasks.

## Physical MP01 audio acceptance

A voice release must not claim real cellular calling until all core checks pass on the owner's main phone.

| Gate | Required observation |
| --- | --- |
| ADB startup | Paired helper can be launched, stopped and reconnected without repeated pairing code when debugging remains enabled |
| Call status | Correct ringing, active and idle state across incoming and outgoing cellular calls |
| Downlink audio | Companion hears the actual distant caller's understandable speech, not just nonzero signal |
| Uplink audio | Distant caller hears understandable speech from the companion microphone |
| Simultaneous audio | Both parties can take turns naturally without losing one direction |
| Audio routes | Speaker, earpiece and mute behave according to UI state |
| Stop behavior | Ending either call or voice stream closes mic, speaker and sockets |
| Reconnect | Repeat calls do not leave a stale active stream |
| Network | Trusted Wi-Fi and separately configured VPN behavior tested distinctly |
| Latency | Subjective intelligibility and measured call delay acceptable |
| Security | No plaintext audio on the media socket, no public HTTP exposure |
| Update | New permanently signed APK installs over preceding signed version without losing pairing |

At least two successful calls in each direction (outbound and inbound) should be recorded before claiming basic function. After that, test call waiting, reboots, low power, locked screen, Bluetooth changes, Wi-Fi calling, cellular-only host networking and task eviction as separate scenarios.

## Safe field report

Record app version on both devices, MP01 Android build identifier, active call type, backend names, audio route, non-identifying level measurements, connection phase, sanitized error strings, and whether each human could hear the other. Do not retain call recordings, identifiers, tokens, contacts or remote person's information.

For `SILENT_OR_MUTED`, a sample count alone is insufficient. Record whether the far-end person spoke during the capture window, whether the audio route switched, and whether the local call speaker remained functional.

For `EOFException`, record whether it occurred during auth, helper startup, PCM startup or steady-state media. Use authorized `voiceStage` and `voiceLastError` fields rather than asking for sensitive logs.

## Failure policy

- Automated tests failing: **no release**.
- Android build failing: **no release**.
- Signature mismatch: **do not install; signing key regression**.
- MP01 audio unverified: **engineering preview only**, no guarantee and no repeated user requests to try speculative builds.
- One audio direction fails: **not working two-way calling**.
- Private network unavailable: **no public-port workaround**.
- Normal Phone app or emergency calling impacted: rollback experimental behavior, keep default dialer unchanged.

A developer should not mark a real-device gate passed based only on CI output or an audio API returning `STATE_INITIALIZED`.
