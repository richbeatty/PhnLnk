# PhnLnk maintainer contract

Last reviewed: 2026-11-08. This is the source of truth for human and coding-agent maintainers.

## Product goal

A Beeper-simple Android companion that makes and answers **real cellular calls**, using the user's existing AT&T number on their existing host phone, with speech through a second existing Android phone or tablet while the host stays at home.

## Non-negotiable requirements

| Requirement | Required behavior |
| --- | --- |
| Existing cellular account | Keep the original SIM, number and carrier. No number porting or paid replacement calling account. |
| Devices | Only the original host phone and an existing secondary Android device. No third computer, headset bridge, dongle, ESP32, SIP gateway or other hardware. |
| Android permissions | No root or Shizuku. No default Phone app role. Basic call control must not require debugging. The owner now permits one explicit, guided Wireless Debugging and six-digit ADB pairing for an optional experimental audio feasibility helper. |
| User experience | Install PhnLnk on both devices, pair once, remain paired until explicitly disconnected. No complex technical administration. |
| Host | Leave the screen off during remote calls when allowed by Android, without PhnLnk waking it or taking over the normal dialer. |
| Calls | Outgoing, incoming, contact dialing, IVR keypad, hangup, ring alerts and genuine two-way audio on secondary device, including away from home. |
| Updates | Same package ID and permanently pinned release signing certificate; new releases install on top without uninstalling and losing pairing. |
| Privacy | Private, authenticated, encrypted communications. No public unencrypted local HTTP listener as an internet-facing shortcut. |

## Current 0.9.3 engineering status (November 8, 2026)

Control and initial pairing now require **pinned HTTPS on 47321**. The host's per-install AndroidKeyStore certificate is verified through the host QR or a full-fingerprint comparison. Existing Android tokens are retained and explicitly migrated once; no plaintext fallback. Both apps must update. This transport identity is separate from the permanent APK signing certificate. See [control security](docs/CONTROL_SECURITY.md). Earlier version-specific HTTP notes below describe history.

V3 repairs helper framework Looper startup; production relay tests cover error-before-close, helper EOF, duplex forwarding, competing clients and revocation. Production protocol tests cover concurrency, fragmentation, timeout, tampering and replay. Native TLS primitives and an offline policy inspector are tested. No physical MP01, AndroidKeyStore migration, screen-off or remote speech result is claimed.

Minimal's published MP01 sources include MT6789 incall playback at 44.1/48 kHz stereo and a fixed-48k call configuration, but other policy variants differ. The installed policy and vendor HAL remain unconfirmed. Independent direct-mode reports include MediaTek success with local output mute off, not this phone. The [MP01 feasibility report](docs/MP01_AUDIO_FEASIBILITY.md) records precise sources, unresolved evidence and the smallest conditional requirement change: OEM-supported firmware/system integration if no authorized route exists. No extra hardware, root, Shizuku, carrier change or forced dialer role was introduced.

See [engineering audit](docs/ENGINEERING_AUDIT.md). Do not send another speculative APK or call PhnLnk functional on build/protocol results.

## Conditional default-dialer consideration (November 7, 2026)

The user would consider making PhnLnk the default Phone app **only if** that enables a genuinely usable dialer plus two-way cellular audio on a second Android device, together with a more polished product experience. This is NOT blanket permission to request the role. Official Android documentation does not grant cellular call capture or injection to an ordinary third-party default dialer. Therefore, PhnLnk should not ask for this role just to make the interface prettier. Keep the ordinary Phone app until a verified feature truly warrants that choice.

## Current reality, not roadmap promises

- **Working in existing prototype:** Local network outbound call request, local paired Android companion, contact search, favorites, incoming call status, answer/end call controls. These were compiled, and outgoing dialing was reported working by the user. Other features have not been comprehensively hardware tested.
- **Missing:** Two-way cellular call audio through the secondary device and an internet-safe relay. Default stock Android 14 apps cannot capture/inject cellular call audio through public APIs. The user has now approved guided one-time Wireless Debugging pairing ONLY for an optional experimental helper. This does not mean cellular audio capture or injection will work. See [Android audio input sharing](https://developer.android.com/media/platform/sharing-audio-input) and [MediaRecorder VOICE_CALL](https://developer.android.com/reference/android/media/MediaRecorder.AudioSource).
- **IVR keypad:** Ordinary keypad entry can work before placing a call. Controlling DTMF during another app's cellular call requires call control access generally granted to the default Phone role. This is not a supported requirement-free feature. Do not prompt for the default role.
- **Previous experimental variants:** v0.5's Linux Bluetooth-to-SIP gateway remains rejected. The v0.6 self-ADB concept is newly allowed as an optional, user-initiated feasibility test, provided the app guides the user, remembers pairing, and retries only while Wireless Debugging remains enabled. Do not promise a working voice path.
- **Host screen:** PhnLnk must never use full-screen call intents, setTurnScreenOn, or wake locks to activate a host display. Android or the phone manufacturer may independently wake it for a cellular call.

## Explicit user approval for guided experimental audio access

**Decision updated November 7, 2026:** The user explicitly requested a guided one-time Wireless Debugging setup with automatic reconnection, despite earlier rejecting manual debugging. This supersedes the earlier no-debugging restriction ONLY for an opt-in advanced audio setup. It is not authorization to silently enable developer mode, automatically approve its own ADB privileges, or require debugging for normal call control.

Workflow: user taps Guided audio setup on the SIM host, grants notification permission, PhnLnk opens Android Developer options, user manually switches on Wireless Debugging and chooses pairing code, and enters the code through a PhnLnk notification while the Android dialog stays open. The pairing identity is persisted. When Android permits it, PhnLnk automatically retries the helper after restart. There must be a visible status, an off switch, and an explicit audio-test consent.

**Android can turn Wireless Debugging off after reboot, Wi-Fi changes, or security changes.** A normal application cannot safely switch it on again or silently authorize itself. Do not make that claim. A shell helper running only proves shell access, not the cellular audio capture/injection or secondary-device calling.

## November 7, 2026: 0.8.1 Android connection stall troubleshooting

User reported PhnLnk stuck on "Connecting to Android" after the guided pairing setup. The earlier ADB port discovery filtered mDNS results using `NetworkInterface.getByInetAddress`, an unnecessary assumption which may reject Android's own advertised address (notably IPv6 / scoped interfaces). The connection was always to loopback regardless of the advertised host. Updated to follow the established JemRec mDNS discovery pattern, connecting **only to 127.0.0.1**.

Additional repairs: bound each ADB shell command's wait to 14 seconds, show specific progress stages instead of an indefinite Connecting spinner, show a 45-second stale-state warning, expose an optional manual **connection** port fallback from the Wireless Debugging main settings screen (not the short-lived **pairing** port), and return brief sanitized shell-helper startup errors. The existing authorized pairing is reused and the app does not request a fresh code unnecessarily.

**Not verified on the user's device.** A mDNS filter is a plausible bug, not a confirmed cause. A successful shell helper still does not prove actual call audio.

## November 7, 2026: 0.8.2 self-ADB "Stream closed" fix

User reported `Connection failed: Stream closed` during guided audio setup in 0.8.1. This matches an independently documented **upstream libadb-android 3.1.1 bug**: https://github.com/MuntashirAkon/libadb-android/issues/34 and its pending patch https://github.com/MuntashirAkon/libadb-android/pull/35 . The underlying symptom is that short-lived `shell:` streams can expose a peer close as IOException, or hang indefinitely after queued data is consumed.

PhnLnk's 0.8.1 version opened an independent `shell:` stream for each helper setup command. v0.8.2 uses an interactive `exec:sh` stream with unique begin/end output markers and only one session per ADB connection, following the independently device-tested JemRec approach, and retries a closed stream once with an entirely fresh ADB connection. Each stage has an error label and bounded wait.

**Do not reintroduce short-lived ADB shell streams** until the underlying dependency is fixed and verified on MP01. Do not claim this transport change proves cellular audio injection, capture, or internet voice streaming. The user may need to keep Wireless Debugging enabled to reconnect.

## v0.9.1 interface and documentation correction

The original host and companion UI had older text claiming voice audio necessarily stayed on the main phone. Since v0.9.0 introduced an experimental encrypted PCM bridge, v0.9.1 updates the UI and README to explain the attempted feature **without** claiming the MP01 has passed a two-way speech test. Both direction tests, screen-off persistence, and remote VPN operation are still open acceptance gates.

## v0.9.0 technical voice transport, November 7, 2026

The user requested implementation of the full audio path rather than further isolated audio tests. Source now includes an **experimental** end-to-end 16 kHz bidirectional PCM relay:

- Shell helper exposes an authenticated loopback-only STREAM command, with redirect, VOICE_DOWNLINK and VOICE_CALL capture options, and redirect or TYPE_TELEPHONY injection.
- Host private-network port 47322 accepts only paired devices via HMAC challenge/response against the saved SHA-256 token fingerprints. Voice PCM is independently encrypted using directional AES-256-GCM with replay-protected packet counters; bearer tokens are not transmitted on the voice socket.
- Companion foreground microphone service starts only after explicit user action and RECORD_AUDIO permission. It plays downlink and sends mic uplink over the authenticated stream, with mute, speaker route and approximate in-band DTMF.
- Pairing revocation disconnects active voice sockets. The normal HTTP call-control transport on port 47321 is unchanged, **unencrypted** and cannot be port forwarded safely. WAN access requires an authenticated private VPN already configured on both devices; the app does not include or provision a public relay.
- Default Phone role remains unrequested. The existing AT&T number, SIM, primary Phone application and phone carrier are unchanged.

**CRITICAL:** The host side had previously reported SILENT_OR_MUTED for VOICE_CALL source. The new routes have NOT been tested on the specific MP01. A successful build, shell helper state, AES voice socket, or successful capture setup cannot prove far-end audible sound or audio injection. Do not advertise full functionality unless both directions are tested on real incoming/outgoing calls. Capture fallback may switch after several seconds of digital silence; caller quietness can also trigger fallback. The companion voice interface is a feature preview, not a verified calling solution.

The user may later permit the default-dialer role only if a real, useful benefit is demonstrated, not merely for receiving normal call control. Keep the old phone app until they explicitly agree.


## November 8, 2026: EOF repair, comprehensive documentation and remote network goal

The user reported `EOFException` after experimental v0.9.1 voice streaming. Without a stack trace, the exact event cannot be attributed to one cause. The source did contain real early-close paths: the shell helper could throw during Android audio initialization without writing a startup UTF response, and a nine-second receive timeout could expire before the companion finished opening its microphone. v0.9.2 adds stage-specific errors, bounded longer startup waits, an error response before STREAM_READY, a strict post-READY guard against mixing UTF with raw PCM, and host/companion diagnostic state. Those changes require automated regression tests and still do not establish MP01 caller audibility.

The user has explicitly requested **extensive GitHub documentation and a handoff prompt for a more capable coding agent**. Documentation is now indexed at [docs/INDEX.md](docs/INDEX.md). The full prompt lives at [docs/CODER_AGENT_HANDOFF_PROMPT.md](docs/CODER_AGENT_HANDOFF_PROMPT.md).

**Separate networks are a core product goal, not a future optional feature.** The host may remain on home Wi-Fi while the companion uses mobile data or another Wi-Fi network. A basic two-device VPN overlay is an incremental engineering route; seamless one-app connectivity likely requires a secure rendezvous/signaling plane and audited encrypted internet transport. At 0.9.2 the control HTTP port was plaintext. Pinned HTTPS supersedes that in 0.9.3, but public port forwarding remains prohibited. See [docs/PHONE_LINK_AND_REMOTE_NETWORKS.md](docs/PHONE_LINK_AND_REMOTE_NETWORKS.md).

**Phone Link research:** Microsoft officially requires nearby Bluetooth for its PC calling feature. An Android HFP client is a hidden/system API often gated by BLUETOOTH_PRIVILEGED, and Bluetooth cannot carry audio when devices are miles apart on separate networks. Its UX is worth studying, not an assumed ready-made audio solution for this project.

The owner specifically stated they do not want another speculative installation or test. Do not offer further installable APKs as verified until there is credible evidence that the MP01 can truly carry both directions of a cellular call.

## Release signing incident and remediation

Versions 0.1 through 0.6 were generally produced with GitHub-hosted runner debug certificates. Those certificates change between builds. An APK update fails when the certificate does not match the installed version. A new APK cannot bypass this rule.

A **persistent private PKCS12 keystore with alias `phnlnk` already exists for this project outside the repository**. The owner must add its two values as Actions secrets:
- `PHNLNK_KEYSTORE_B64`
- `PHNLNK_STORE_PASSWORD`

The repository's release workflow **must fail closed** and refuse to publish a GitHub Release unless these secrets are present and a signed release APK has passed `apksigner verify`.

**One last uninstall** will still be necessary to move from a prior runner-debug-signed app to the first permanent-key-signed installation. After that, updates using the same retained private certificate, same package ID, and increasing version codes should install over the previous version. Don't promise otherwise.

Do NOT commit signing material, add it to releases, or log it. Maintain a secure offline backup of the permanent private key. Subsequent builds must reuse it; never regenerate it per workflow run.

## Required test gates

1. `python3 tools/maintainer_check.py` passes.
2. Voice protocol/relay, control TLS and offline policy tests pass. The Android debug APK compiles and its bundled assets are verified in CI. A debug artifact is not a release.
3. For a GitHub Release, the same permanent key is used and `apksigner verify --verbose --print-certs` passes.
4. Verify `applicationId` and `versionCode` invariants.
5. For any claimed new cellular audio path, obtain an on-device test of live remote speech in both directions, incoming and outgoing. The build passing is not sufficient.
6. Before claiming persistence or sleep behavior, test reboot, Wi-Fi reconnection, screen off, standby, and companion notifications.
7. Document any limitations plainly in release notes and README.

## Decision log

| Date | Decision | Reason |
| --- | --- | --- |
| 2026-11-08 | Pinned HTTPS and preserved-token migration | Eliminate plaintext pairing/control while retaining saved Android authorization |
| 2026-11-07 | No extra hardware | User expressly rejected Linux/Bluetooth gateway approach |
| 2026-11-07 | No Shizuku or root; optional guided debugging approved | User explicitly accepted one-time Android pairing with automatic reconnection for an experimental audio path |
| 2026-11-07 | No default Phone app role | User requires retaining stock dialer |
| 2026-11-07 | Maintain persistent repo instructions | Prevent recurring regressions during rapid prototyping |
| 2026-11-07 | Signed GitHub releases only | Stop shipping APKs with incompatible, temporary certificates |
| 2026-11-07 | Keep all incomplete audio routes clearly labeled | Avoid false promises to users |
| 2026-11-07 | Research cloud VoIP forwarding and Beeper Android APIs | Distinguish verified-number internet calling from host modem audio; requires opt-in provider billing |

## Comprehensive external solutions research

See [CALL_RESEARCH_2026.md](docs/CALL_RESEARCH_2026.md) for the November 7, 2026 investigation of Beeper, Google Messages, AT&T NumberSync, T-Mobile DIGITS, Google Fi Web Calls, Android's audio policies, GitHub cellular gateways and low-cost verified-caller-ID VoIP.

The closest technically supportable voice route without more hardware or privileged Android setup is AT&T inbound call forwarding to a hosted VoIP endpoint plus verified AT&T number as outbound caller ID. **This requires an additional paid voice provider and an AT&T forwarding configuration, so it is only an opt-in alternative, not a silent change to the user's nonnegotiable requirements.** No account was set up, no call forwarding was enabled, and the prototype has not implemented this path. It is not the host SIM's outgoing audio; the cloud provider handles that leg.

Beeper officially does not support voice calling. Its Android Content Provider is a legitimate optional messaging integration, with user-granted permissions, and does not unlock cellular audio.

## Interface refresh

v0.7 adds a dependency-free native shared visual system (`PhoneUi.java`) and redesigned host and companion screens. The keypad, contacts, favorites, recent dial requests, live call state, pairing, and connection settings must be maintained. The first-run role selection is device-neutral.

No native screen polish should be marketed as solving the cellular audio restriction. Report only source compilation until an actual host and companion are tested.

## Maintenance rule

This file is meant to persist across future work. Coding agents should read `AGENTS.md` and this file first, update the decision log when the owner changes requirements, and never override the existing contract without explicit new user direction. GitHub Actions checks guard against a subset of known regressions; they do not replace human review or hardware testing.
