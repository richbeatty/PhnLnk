# Cellular-call bridging research

Research date: November 7, 2026. These are third-party projects, not imported or endorsed code.

## Projects assessed

| Project | What it proves or offers | Fit for MP01 |
| --- | --- | --- |
| [sparkcyf/android-voice-sip-agent](https://github.com/sparkcyf/android-voice-sip-agent) | Cellular <-> SIP, two-way audio, incoming and outgoing. Uses Shizuku shell privileges and hidden Android telephony audio paths. GPL-2.0-or-later. | Most capable core, but expects a PBX, and no MP01 validation. |
| [Hinduc0der/voice-sip-agent-selfhost](https://github.com/Hinduc0der/voice-sip-agent-selfhost) | Fork with built-in SIP registrar and incoming voice/SMS. Tested on Nubia Android 16 without root. | Removes PBX, but remote outgoing calls are not yet implemented. Internet calling not validated by maintainers. |
| [neyro-droid/P2P-PHONE-Call-Gateway](https://github.com/neyro-droid/P2P-PHONE-Call-Gateway) | Local browser dialer, call controls, and a no-root acoustic audio workaround. | UI/control inspiration; no-root audio depends on speaker and microphone and is unsuitable for a dependable call bridge. |
| [kitsumed/ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder) | Unrooted capture of uplink/downlink on some phones via Shizuku. GPL-3.0. | Relevant to diagnosing capture but does not inject microphone audio or handle remote calls. |
| [pulpoff/gsm2sip](https://github.com/pulpoff/gsm2sip) | Rooted modem/DSP audio routing across several chip families, incoming/outgoing voice and SMS. | Good fallback research if an audio path is unavailable without root. Rooting is not the intended MP01 requirement. |
| [Agora-Build/DialF](https://github.com/Agora-Build/DialF) | Call control over Wi-Fi with a physical USB audio bridge. | Not an app-only solution. |
| [FarisHijazi/robocall](https://github.com/FarisHijazi/robocall) | Linux host with Bluetooth HFP audio to an Android phone. | Requires an always-on nearby Linux computer; useful as a contingency. |

## Technical constraints

Ordinary third-party Android apps cannot directly capture cellular voice audio using public APIs. Shizuku can grant shell-level access used by some open-source projects with undocumented telephony routes, but these vary by device firmware and OS update. Seeing TELEPHONY_TX or TELEPHONY_RX in dumpsys is only a candidate check, not a pass.

The MP01 is unverified. First validate device CPU and call audio route. Then use the upstream SIP agent to test an actual incoming and outgoing cellular call with two-way audio, using a safe local network and test endpoints. The privileged solution must not silently answer calls until the user enables it.

A future PhnLnk client should support both Android tablets and desktop browsers. For voice, a secured SIP client backed by an agent or a WebRTC gateway is a more credible path than writing a new call-audio interception layer from scratch.

Security goals for that future architecture: authenticated paired clients, explicit call takeover, encrypted transport, no open WAN SIP port, no default unrestricted dial-out, no stored audio, graceful failure when host sleeps or Shizuku stops, and clear rejection of emergency dialing.

## MVP delivered here

The Android host and browser dialer provide authenticated **control only**. Their simple API (`/api/status`, `/api/dial`, `/api/answer`, `/api/end`) is not an audio transport. This allows immediate MP01 testing without claiming that voice has been bridged.
