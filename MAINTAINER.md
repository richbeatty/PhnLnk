# PhnLnk maintainer contract

Last reviewed: 2026-11-07. This is the source of truth for human and coding-agent maintainers.

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
2. The Android debug APK compiles and its bundled assets are verified in CI. A debug artifact is not a release.
3. For a GitHub Release, the same permanent key is used and `apksigner verify --verbose --print-certs` passes.
4. Verify `applicationId` and `versionCode` invariants.
5. For any claimed new cellular audio path, obtain an on-device test of live remote speech in both directions, incoming and outgoing. The build passing is not sufficient.
6. Before claiming persistence or sleep behavior, test reboot, Wi-Fi reconnection, screen off, standby, and companion notifications.
7. Document any limitations plainly in release notes and README.

## Decision log

| Date | Decision | Reason |
| --- | --- | --- |
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
