# Troubleshooting

Use this guide to identify the failing layer before making a new build. PhnLnk has separate host call controls, Android self-ADB, privileged audio backends, encrypted voice transport and companion audio APIs.

The user has stated that they do not want to keep installing speculative builds. Treat this guide as maintainer-facing until the underlying device compatibility is established. Do not promise a code-only fix to an unknown vendor HAL restriction.

## Quick diagnosis table

| Screen or error | Layer | Meaning and next investigation |
| --- | --- | --- |
| `Connecting to Android...` for a long time | Host local debugging | Check saved pairing, current Wireless Debugging setting, mDNS port discovery, current connection port |
| `Stream closed` during self-ADB | ADB command stream | Check persistent `exec:sh` implementation, local adbd reboot, libadb race and retry logic |
| `Helper ready. Call audio not verified` | Shell process | Local shell helper responded to PING. Neither cellular audio direction was tested |
| `SILENT_OR_MUTED` | Shell cellular downlink | Samples arrived with insufficient measured energy; may be a blocked source or a silent caller |
| `AUDIO_INIT` or `BACKEND_REJECTED` | Shell audio setup | Audio source or output route failed during initialization; inspect exact backend |
| `HELPER_CLOSED` | Host to local helper | Helper closed before sending expected STREAM startup acknowledgement |
| `VOICE_HANDSHAKE_CLOSED` | Encrypted client authentication | Host socket closed before full greeting or proof; check both app versions and stored pairings |
| `PAIRING_REJECTED` | Encrypted voice authorization | Host did not recognize the companion token or proof |
| `NO_CALL` | Host call state | No currently active cellular call |
| `VOICE_MEDIA_CLOSED` | Established encrypted stream | Peer closed before a complete next frame; check host last error and call state |
| `VOICE_MEDIA_TIMEOUT` | Media timing | No encrypted audio frame received within the bounded wait |
| `UPLINK_STOPPED` or `DOWNLINK_STOPPED` | Host PCM | One half of host media loop exited and closed the session |
| `EOFException` alone | Legacy generic network error | Identify whether shell readUTF, handshake read, or PCM read ended first; no single cause can be inferred |
| Far-end hears nothing | Audio injection | Wrong telephony output device, vendor policy or routing; successful write is insufficient |
| Companion hears nothing | Audio capture | Wrong downlink source, digital silence, vendor HAL or permissions |
| Wrong audio in headset or speaker | Companion routing | Revisit AudioManager communication-device selection and audio effects |

## Reported EOFException: source analysis

Before the 0.9.2 changes, at least two code paths could produce an unhelpful EOF:

1. **Shell helper exception before acknowledgement.** The shell's `STREAM` operation called `shellContext()` and Android audio backend initialization. An exception could propagate to the outer handler, which logged the failure but closed the socket without returning a UTF error. The host's `shellIn.readUTF()` then threw `EOFException`. The host could close the encrypted connection, leaving the companion with an even more ambiguous EOF.
2. **Companion mic startup lag.** The shell voice socket previously allowed a nine-second input read timeout after declaring STREAM_READY. If the companion audio stack took longer to initialize microphone capture, the shell receiver timed out, closed the stream and the host followed.

Changes in 0.9.2 make shell startup exceptions report an explicit `ERROR:AUDIO_INIT:...` where possible, map early close to `HELPER_CLOSED`, record the host phase and last error, and give the companion a longer bounded startup grace period. The app can query the authorized host status if the voice session closes unexpectedly. See [EOF incident](EOF_INCIDENT_2026-11-08.md).

**Evidence limit:** These are confirmed issues in source code, not proof that either caused the user's exact device error. Without the actual stage and logs, another cause remains possible.

## Which connection failed?

### Basic call-control HTTP

If the companion cannot reach the host's private IP, check the host service, Wi-Fi, cellular hotspot isolation, VLAN separation, VPN route, or a stale saved IP. Host control uses port 47321. Connection authorization is distinct from the Android ADB identity.

If you see a 401, the paired HTTP token may have been revoked. Do not confuse it with a failing shell helper. Do not print or paste the token.

### Host Wireless Debugging

The app cannot turn Android Wireless Debugging on without privileged access. After reboot or Wi-Fi changes, Android may turn it off. The Android pairing identity can still be saved even when debugging is unavailable.

The **Wireless Debugging connection port** shown on the main Settings screen is not the temporary port shown in the six-digit pairing popup. Enter the connection port only if automatic mDNS discovery fails. The host ADB connection targets 127.0.0.1, not neighbors' ADB servers.

### Shell helper

A PING READY does not imply audio permission. The shell helper checks UID 2000 before accepting requests. If the bundled helper version changed, PhnLnk must stop and replace its previous process. A stale V1 helper cannot implement V2 STREAM commands.

Inspect the host's authorized `voiceStage` and `voiceLastError` from `GET /api/status`. Do not assume an old `helper_ready` preference means a current process exists.

### Encrypted voice handshake

Both devices should run the same app build. The client sends a proof based on its saved token. The host has the corresponding hash. If tokens do not match, the host closes without revealing protected state and the companion should report a handshake-stage error.

Do not disable authentication to resolve an EOF. Do not switch the voice socket to plaintext or expose 47322 to the internet.

### Cellular audio

A quiet call participant can make RMS tests inconclusive. Signal detection alone can also be a local microphone rather than the far-end caller. Only a consenting person speaking during a real call and independently confirming the injected test tone can validate capture and injection.

Try the experimental routes methodically: REDIRECT, DOWNLINK and CALL for incoming sound; REDIRECT and TELEPHONY for outgoing. All results remain device-specific.

## Diagnostic data that is safe to report

- PhnLnk app version on both devices.
- Host Android version and build number.
- Host `voiceStage` and **sanitized** `voiceLastError`, if available.
- Host `callState` (idle, ringing, active).
- Current selected audio backend labels.
- RMS and peak values without storing or sending actual speech.
- Whether Wireless Debugging was already enabled.
- Whether the error happened during handshake, after READY, or during an active call.

**Never share:** pairing codes, bearer tokens, shell secret, app private preference files, full call recordings, private contacts, phone numbers, or raw ADB authorization material. Remove these from screenshots and bug reports.

## What not to do

- Do not uninstall PhnLnk just to retry a voice stream. Uninstalling can erase pairing state.
- Do not claim connected shell, matching crypto and build success prove remote cellular speech.
- Do not open raw host TCP ports to the public internet.
- Do not take over the default Phone role to chase unavailable audio privileges.
- Do not disable TLS-equivalent media encryption, per-device authorization, or sequence checks to "fix" a connection error.
- Do not repeatedly test a user's personal calling service without a credible root-cause hypothesis.
