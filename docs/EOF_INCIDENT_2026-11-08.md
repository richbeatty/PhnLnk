# EOFException analysis and source changes

**Date:** November 8, 2026.

**Reported symptom:** The user reported `eofeXCEPTION` after the experimental 0.9.1 voice implementation. No stack trace, host voice phase, exact screen, call-state details or MP01 device logs were supplied. Do not invent those missing details.

## Executive summary

An `EOFException` means a reader expected bytes from a TCP stream and encountered end-of-stream first. It does not uniquely identify the cause. It may occur when the host rejects an unauthorized companion, the shell audio backend fails before sending its acknowledgement, the service ends a call, a peer crashes, or one side times out and closes.

Review of the 0.9.1 source established **two concrete code-level paths** that can produce an early close without a useful explanation.

## Root-cause candidate A: silent shell startup failure

### Previous behavior

The host's `VoiceRelayServer` sent `STREAM:<capture>:<uplink>` to the shell helper, then called `shellIn.readUTF()` expecting `STREAM_READY` or `ERROR`.

Inside `AudioProbe.handle`, `stream()` called `shellContext()` and opened Android audio backend objects. When `shellContext()` threw, or a vendor-specific audio initialization exception escaped before the explicit backend errors were emitted, the outer handler logged it and implicitly closed the socket.

The host then received EOF while trying to read a UTF acknowledgement. It had no stage-specific reason to show the companion.

### Correction in 0.9.2

- Wrap the shell STREAM startup in a dedicated catch and attempt `ERROR:AUDIO_INIT:<short exception>` if readiness has not begun.
- Mark whether the STREAM_READY response has started so a late exception cannot inject a text error into raw PCM.
- The host maps local helper EOF during startup to `HELPER_CLOSED`, and a bounded timeout to `HELPER_TIMEOUT`.
- The host sends one encrypted ERROR frame to the authorized companion before media READY when possible.
- The companion retrieves sanitized last-stage / last-error fields from the authenticated host status API when an otherwise unexplained closure occurs.

### What this establishes

The original source had a real premature-close path. The new code should report the underlying failure more clearly. Without logs, this does not prove that the user's specific EOF came from that path or that the alternate capture methods are usable.

## Root-cause candidate B: too-short PCM startup timeout

### Previous behavior

The V2 shell helper replied STREAM_READY as soon as it opened its capture and injection objects. It then set its socket read timeout to nine seconds while waiting for microphone frames from the companion.

The companion only began creating its own `AudioRecord`, noise suppression, echo cancellation, `AudioTrack` and audio routing **after** receiving READY. If this setup took longer than the shell's input wait, the shell receiver timed out and closed the connection, even if the protocol and privileges were correct.

The host then closed the encrypted TCP socket. The companion could see a generic EOF.

### Correction in 0.9.2

- Use a bounded, longer PCM startup/read grace period inside the shell helper.
- Increase host wait for shell backend startup.
- Increase companion wait for the initial authenticated host control frame.
- Keep separate steady-state timeouts and emit explicit timeout-specific errors.

These changes allow more realistic Android initialization delays. They do not solve a permanently silent source, blocked HAL or a process that never initializes.

## Other possible EOF sources

1. **Auth mismatch:** The host rejects a device token it does not recognize, resulting in an intentional close before the server proof. The companion now identifies `VOICE_HANDSHAKE_CLOSED at WAIT_HOST_AUTHORIZATION`.
2. **Different versions:** The host may not recognize the new `PHN1` voice protocol, or one device may run a build without TCP 47322. The companion names the handshake phase and recommends matching versions.
3. **No active cellular call:** The host sends an encrypted `NO_CALL` error and closes normally.
4. **ADB helper exited:** A previously ready state can become stale after Android stops debugging or the helper.
5. **Cellular audio policy:** A route may fail or return zeros while protocol transport is otherwise sound.
6. **Wi-Fi or VPN loss:** A socket can be reset or closed during media. This cannot be prevented by Android audio changes.
7. **Call ended normally:** End-of-stream can be an expected result. It should not be labeled a successful full-duplex test.

## What changed and where

| File | Change |
| --- | --- |
| `shellprobe/src/.../AudioProbe.java` | Startup error response, post-READY guard, longer bounded PCM socket timeout |
| `app/src/.../VoiceRelayServer.java` | Host error phase, explicit helper-close / timeout errors, sanitized last error |
| `app/src/.../VoiceSecureTransport.java` | Staged handshake and media EOF exceptions, initial backend startup grace |
| `app/src/.../VoiceSessionService.java` | Companion phase reporting and authorized host status lookup on unexpected close |
| `app/src/.../CallController.java` | Authorized `voiceStage`, `voiceLastError` and timestamp in status |
| `app/src/.../ClientActivity.java` | Keep failed voice status visible during active calls and avoid reused View parent crash |
| `tools/voice-tests/` | Pure JVM loopback test harness for real voice protocol code |
| `.github/workflows/android.yml` | Add protocol tests to normal CI sequence |
| `docs/` | Expanded documentation and regression contract |

## Regression tests

The JVM protocol suite compiles production `VoiceSecureTransport.java` against minimal stubs and exercises actual socket traffic:

- Correct HMAC and encrypted roundtrip.
- EOF before the first host greeting.
- EOF after client proof but before server proof.
- Wrong pairing identity rejection.
- EOF after media handshake.
- Rejection of tampered GCM frame.
- Rejection of replayed sequence number.

A separate source-level maintainer check enforces the early shell error response, stage reporting and guard against extra UTF output after READY.

These tests are meaningful, but do not execute Android AudioRecord, the MP01 HAL or a real carrier call.

## Open acceptance criteria

- Confirm the exact stage and root cause of the user's reported EOF, if further direct evidence ever becomes available.
- Confirm host shell helper actually runs and sends proper startup responses after Android updates.
- Verify Android audio routing with a consenting far-end caller on the exact MP01.
- Validate both voice directions together, screen-off operation, incoming calls and cleanup.
- Avoid repeatedly requesting device testing until software checks and independent compatibility research justify it.

The issue should not be closed merely because the build passes. See [Troubleshooting](TROUBLESHOOTING.md), [Test plan](TEST_PLAN.md) and [Known limitations](KNOWN_LIMITATIONS.md).


## Additional source defects confirmed in 0.9.3

The helper called `ActivityThread.systemMain()` on an unprepared worker. Android 14's ActivityThread constructs Handlers, which require a Looper. V3 creates framework context on a prepared main Looper and keeps callbacks running. A PING-only helper could previously look ready before this failure.

Separately, VoiceRelayServer's try-with-resources closed the companion socket before its catch block tried to send an encrypted startup error. Moving close into finally lets that error reach the companion. Production relay tests now simulate a helper connect failure and an early EOF and assert the encrypted error text arrives. Neither is proven to be the cause of the owner's particular EOF without its trace.
