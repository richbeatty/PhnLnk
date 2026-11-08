# Android cellular audio backends

## Why this is the main unresolved issue

The MP01's stock Android call audio follows a vendor telephony policy and hardware abstraction layer (HAL). A normal third-party application can request microphone access, but it cannot normally read the protected cellular downlink or send raw PCM into the cellular uplink.

Making an app the default Phone application does not itself grant the privileged capture and injection permissions. This is why PhnLnk keeps the regular Phone app and uses an explicitly user-approved, experimental shell UID 2000 helper for optional voice access.

The user's first `VOICE_CALL` test reported `SILENT_OR_MUTED`. That only established that the single tested route returned an insufficient audio level at that moment. It did not establish that the phone supports the other routes or that any chosen route will carry the remote party's actual voice.

## The helper process

The bundled DEX is built by `shellprobe/build.sh` and packaged as `assets/phnlnk-probe.jar`. When explicitly authorized, the app launches it under Android shell UID 2000 through the local ADB connection. The helper refuses an unexpected UID and binds only to 127.0.0.1:47327.

The helper's private socket accepts a per-install random secret, followed by one of:

- `PING`: Process readiness, not call-audio readiness.
- `CAPTURE`: Measures input signal levels and summarizes candidate capture backends.
- `INJECT`, `INJECT:REDIRECT`, `INJECT:TELEPHONY`: Attempts a brief quiet test tone. Caller must consent and audibility must be confirmed by the person at the other end.
- `STREAM:<capture>:<uplink>`: Opens the selected bidirectional PCM audio paths.
- `STOP`: Stops the helper and closes its listening socket.

Audio content is not saved to a file by these operations. The host app terminates the loopback socket and handles network encryption separately.

## Available downlink options

| Backend label | Mechanism | Main limitation |
| --- | --- | --- |
| `REDIRECT` | Reflective `AudioManager.getCallDownlinkExtractionAudioRecord(AudioFormat)` | Requires compatible Android interception APIs, attribution and device permission policy |
| `DOWNLINK` | `AudioRecord` with `MediaRecorder.AudioSource.VOICE_DOWNLINK` | Privileged source can fail, initialize but be muted, or route differently on vendor ROM |
| `CALL` | `AudioRecord` with `MediaRecorder.AudioSource.VOICE_CALL` | May capture zeros even when the call is active; original MP01 diagnostic was silent |
| `AUTO` | Tries REDIRECT, then DOWNLINK, then CALL | Initializes the first available source, not necessarily the first audible source |

A live streaming session can try the next capture option after sustained digital silence. Silent intervals can also be genuine pauses in the conversation; switching backends is not proof of correctness.

The first version of the helper attempted only `VOICE_CALL`. This was expanded to include the other methods after the reported silent measurement. No MP01 result for the new methods has yet been recorded.

## Available uplink options

| Backend | Mechanism | Main limitation |
| --- | --- | --- |
| `REDIRECT` | Reflective `AudioManager.getCallUplinkInjectionAudioTrack(AudioFormat)` | Device may expose a track without the far-end caller hearing it |
| `TELEPHONY` | `AudioTrack` with telephony output selected as preferred device | Vendor audio policy may reject or redirect the track |
| `AUTO` | Tries REDIRECT, then TELEPHONY | Successful write only confirms local acceptance of the samples |

The test reports how many samples were written and the requested route. It cannot detect whether the remote party actually heard the tone. That evidence must come from the call participant.

## Android attribution matters

The helper constructs an Android `Context` attributed to `com.android.shell`, matching its UID. Android's audio app-ops checks may consult the attribution source, package name and permission policy. A mismatched source can lead to an exception, a denied recording or an initialized recorder that yields silence.

The helper catches audio backend initialization exceptions and reports them through the local startup protocol where possible. Its failure reasons are surfaced by the host as `AUDIO_INIT`, `BACKEND_REJECTED`, or other stage-specific errors.

Attribution and vendor HAL behavior are separate concerns. Fixing attribution may allow initialization but will not necessarily make the cellular stream audible.

## Timing and lifecycle

- Start the diagnostics with a real, active cellular call, not merely a ringing phone.
- Use a consenting person, not an automated service, for the audibility test.
- Allow extra time for audio backend initialization before enforcing the steady-state media timeout.
- Keep a stock Phone call operational if the helper closes; the voice experiment must not pretend to be an emergency dialer.
- The companion microphone initializes after the host sends READY. The helper must tolerate this setup period instead of terminating the socket immediately.
- A result showing a sound level can be local microphone noise rather than downlink speech. A live far-end voice comparison is required.

## Device compatibility evidence

| Device | Android | Observation | Confidence |
| --- | --- | --- | --- |
| Minimal Phone MP01 | Android 14 | User reported original `VOICE_CALL` result as silent or muted; local helper became ready | Reported result, not a complete voice test |
| Other vendor phones | Varies | Open-source gateways show that certain privileged audio paths can work on selected firmware | Not transferable to MP01 without evidence |

This project must not convert an unrelated device's successful call test into a success claim for the MP01. See [Test plan](TEST_PLAN.md) for the proof required.


## 0.9.3 source investigation

Helper V3 prepares and pumps a framework Looper and caches its shell context before socket workers run. The prior worker-side `systemMain()` could fail during Handler construction before audio initialization. This repair does not prove the MP01 HAL path. Diagnostics and streaming now share one audio-operation ownership gate.

Minimal's public MT6789 policy declares incall playback at 44.1/48 kHz stereo, differing from the current helper's 16 kHz mono request. Another common policy has no software playback mix into Telephony Tx. The installed policy and HAL conversions remain unknown. `AUDIO_DEVICE_IN_VOICE_CALL` is a native alias for TELEPHONY_RX, not proof of an absent Rx port. See the [MP01 feasibility report](MP01_AUDIO_FEASIBILITY.md) for pinned manufacturer and Android sources, external direct-mode evidence and the conditional OEM requirement change.
