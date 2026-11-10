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

## Current automated production-code tests

0.9.4 adds eight production native-adapter/helper-version cases to the voice test script. They verify 20 ms / one-second duration, stereo duplication, arbitrary chunk boundaries, full-scale/DC behavior, bounded impulse response and 1 ms delay, 1 kHz passband with image suppression, session isolation and replacement of V1/V2/V3 while reusing V4. This brings the distinct automated case count to 51; the five TLS cases run for both TLS versions. Native HAL routing remains device-unverified. See [0.9.4](TEST_BUILD_0.9.4.md).

`bash tools/test_voice_protocol.sh` compiles actual `VoiceSecureTransport.java`, `VoiceRelayServer.java` and `SocketDeadline.java`. Android call state, preferences and the helper's hardware PCM endpoint are substituted; networking and cryptography are real loopback TCP.

- 22 protocol cases: encrypted roundtrip; staged client and host handshake EOF; wrong pairing; forged host proof; media EOF; tampering; replay; fragmented simultaneous duplex; six concurrent senders; six truncation positions; four invalid lengths; bounded media timeout.
- 9 relay cases: no-call error; access-off error; helper connect exception; helper early EOF; helper initialization refusal; stalled unauthenticated client alongside valid auth; authentication worker quota; absolute deadline against dribbling proof; live duplex forwarding with encrypted busy refusal and closure of active/pending sockets on revocation. Each scenario starts/stops the actual production listener, exercising rapid rebinding.

`bash tools/test_control_tls.sh` compiles actual `ControlTls.java` and generates ephemeral test EC certificates, unrelated to release signing. Five cases cover strict pin validation, valid TLS application bytes and session identity verification, changed-host rejection before request, discovery without application payload, and unchanged default CA trust.

`python3 tools/test_audio_policy.py` has seven tests of the offline policy inspector, including device aliases, missing playback routes, native profiles and unresolved includes. Added cases prevent combining fields from different profiles, preserve unknown/dynamic declarations and replay the recorded MP01 platform/common policy differences. It never claims audibility.

The TLS certificate-rejection cases permit either a TLS alert or server-side TCP SocketException: TLS 1.3 can still be writing Finished when the correctly rejecting client closes. Client-side SSLHandshakeException remains required. A received application byte still fails the changed-identity test. These expected server failures are not permitted in positive traffic or discovery tests.

These tests are in CI before Android compilation. No tests claim a modem, microphone, AndroidKeyStore or physical MP01 was exercised.

## Integration regressions to catch

- Old V1/V2/V3 helper is replaced by V4 on a version upgrade without clearing the stored Android debugging identity.
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
| Security | Pinned HTTPS for code/token/control, encrypted media, changed identity rejected, no public ports |
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


## Measurable acceptance record (all currently open)

Use a consenting far-end participant and an independent developer/OEM MP01 before proposing another owner install. Record dates, app commit/version, installed Android/vendor build and policy hash, backend/native formats, network type and pass/fail. Keep numbers, contacts, tokens, ADB identities and recordings out of shared evidence. These are proposed thresholds, not measured results:

| Gate | Procedure and pass threshold |
| --- | --- |
| True downlink | Far-end speaker reads 10 unpredictable words while host mic is acoustically isolated; companion listener identifies at least 9. Local host noise alone must not count |
| True uplink | Companion speaker reads 10 different unpredictable words; far-end listener identifies at least 9 with the host mic isolated |
| Negative control | Companion mute prevents companion speech from reaching far end while downlink remains audible; restore mute without reopening call |
| Full duplex | Five minutes of natural two-way speech with interruptions and overlap; no lost direction, host acoustics shortcut, or involuntary call end |
| Delay/quality | Proposed one-way additional bridge delay target under 300 ms at the 95th percentile; record timing method and network baseline separately |
| Call matrix | At least two incoming and two outgoing calls for each of same Wi-Fi, host home Wi-Fi/companion cellular, and host home Wi-Fi/companion unrelated Wi-Fi |
| Controls | Answer, reject using End, hangup from each side, mute, route selection and a consenting IVR DTMF sequence; preserve SIM caller ID and stock Phone app |
| Notifications | Ten incoming calls while companion locked; record delivery delay and failures. Target notification within 5 seconds when connected; do not assume polling survives Doze |
| Network change | Wi-Fi/cellular transitions and 30-second tunnel loss; visible disconnect within timeout, microphone stops, no duplicated call command or redial, deliberate recovery |
| Background | 30 minutes screen off, service restart, reboot and battery restrictions; document OS-controlled debugging/VPN limits rather than promising automatic recovery |
| Revocation | Active media closes immediately; pending auth closes; subsequent control denied. Already dispatched Telecom commands are not cancellable |
| TLS migration | Existing token/pairing survives update; verify once; wrong certificate fails before sending token; changing private address retains pin |
| Signed update | Same permanent certificate, unchanged applicationId, higher versionCode; pairing, preferences, ADB identity and TLS identity survive in-place installation |

A failed or missing speech direction blocks the two-way calling claim regardless of TLS, VPN, RMS, capability flags, successful writes or PCM frame counts. See [MP01 feasibility](MP01_AUDIO_FEASIBILITY.md).
