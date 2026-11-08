# Cellular audio testing protocol

This document outlines a controlled compatibility test. The user has requested **no further speculative installation or call tests until credible evidence exists**. These steps are a future validation plan, not an instruction to run another test now.

## Distinct signals

| Signal | What is actually established | What remains unknown |
| --- | --- | --- |
| `READY:V3:shell UID 2000` | Local privileged helper process is running | Recording/injection access |
| `CAPTURE` with sample count | An AudioRecord returned samples | Their audio source, far-end speech, intelligibility |
| `SIGNAL_DETECTED` | PCM RMS and peak exceeded the diagnostic threshold | Whether this is the remote person or local room |
| `INJECT` with positive written samples | The requested track accepted PCM | Whether the far-end person heard the tone |
| `STREAM_READY` | Capture and injection objects initialized | Whether either route actually carries carrier audio |
| Encrypted voice connected | HMAC and AES stream initialized | Human audibility, stability, latency |
| Both people hear each other | Actual voice function during the observed call | Future calls, other networks and screen-off behavior |

## Controlled test conditions

- Use the actual main-phone device and OS build to which any result will be attributed.
- Keep the stock Phone app and existing SIM unchanged.
- Enlist an informed, consenting other person on a non-emergency test call.
- Do not record or upload call audio. Prefer short level readings and direct verbal confirmation.
- Record the Android build, firmware, audio route and whether the test was cellular or Wi-Fi calling.
- Do not use the six-digit Android debugging code, app bearer token or private ADB secret in a bug report.

## Downlink candidate sequence

1. Verify the host is in an active call.
2. Have the distant party speak during the capture measurement.
3. Test `REDIRECT`, `DOWNLINK` and `CALL` independently if the initial AUTO route is silent.
4. Record sample count, RMS, peak, error, and exact backend.
5. Only count success if the **companion actually plays the distant caller's understandable speech**. An arbitrary nonzero amplitude is insufficient.

## Uplink candidate sequence

1. Inform the distant party that a brief quiet tone will be attempted.
2. Select `REDIRECT` or `TELEPHONY` injection.
3. Send the short consented tone and record the API result.
4. Ask the distant party whether they actually heard it. A successful write is not enough.
5. Validate that ordinary speech from the companion reaches the far-end party, not only the test tone.

## Full-duplex and reliability

After each direction independently passes, keep both paths open and perform an actual conversation. Check normal speaking, silence periods, speaker/earpiece, microphone mute, hangup, repeated calls, incoming calls, network changes and the host's screen off.

No full-duplex success has yet been documented on the owner's MP01. Do not label any generated APK as verified solely from a passing build.

## Regression tests before any device work

Run `bash tools/test_voice_protocol.sh`, `python3 tools/maintainer_check.py`, and CI Android builds. These protect the cryptographic transport and startup error handling from obvious software regressions. They cannot prove vendor HAL support.

See [Test plan](TEST_PLAN.md), [Troubleshooting](TROUBLESHOOTING.md) and [Known limitations](KNOWN_LIMITATIONS.md).
