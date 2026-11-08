# PhnLnk maintainer contract

Last reviewed: 2026-11-07. This is the source of truth for human and coding-agent maintainers.

## Product goal

A Beeper-simple Android companion that makes and answers **real cellular calls**, using the user's existing AT&T number on their existing host phone, with speech through a second existing Android phone or tablet while the host stays at home.

## Non-negotiable requirements

| Requirement | Required behavior |
| --- | --- |
| Existing cellular account | Keep the original SIM, number and carrier. No number porting or paid replacement calling account. |
| Devices | Only the original host phone and an existing secondary Android device. No third computer, headset bridge, dongle, ESP32, SIP gateway or other hardware. |
| Android permissions | No root, Shizuku app, Wireless Debugging, Developer options, ADB pairing, or default Phone app role as a requirement for the product. |
| User experience | Install PhnLnk on both devices, pair once, remain paired until explicitly disconnected. No complex technical administration. |
| Host | Leave the screen off during remote calls when allowed by Android, without PhnLnk waking it or taking over the normal dialer. |
| Calls | Outgoing, incoming, contact dialing, IVR keypad, hangup, ring alerts and genuine two-way audio on secondary device, including away from home. |
| Updates | Same package ID and permanently pinned release signing certificate; new releases install on top without uninstalling and losing pairing. |
| Privacy | Private, authenticated, encrypted communications. No public unencrypted local HTTP listener as an internet-facing shortcut. |

## Current reality, not roadmap promises

- **Working in existing prototype:** Local network outbound call request, local paired Android companion, contact search, favorites, incoming call status, answer/end call controls. These were compiled, and outgoing dialing was reported working by the user. Other features have not been comprehensively hardware tested.
- **Missing:** Two-way cellular call audio through the secondary device and an internet-safe relay. Default stock Android 14 apps cannot capture/inject cellular call audio through public APIs. A shell-level workaround also violates the user's explicit no-debugging rule. See [Android audio input sharing](https://developer.android.com/media/platform/sharing-audio-input) and [MediaRecorder VOICE_CALL](https://developer.android.com/reference/android/media/MediaRecorder.AudioSource).
- **IVR keypad:** Ordinary keypad entry can work before placing a call. Controlling DTMF during another app's cellular call requires call control access generally granted to the default Phone role. This is not a supported requirement-free feature. Do not prompt for the default role.
- **Previous experimental variants:** v0.5's Linux Bluetooth-to-SIP gateway and v0.6's self-ADB helper violate the product constraints. Keep them in history for research, not in first-run flow and not as claims of meeting the user requirements.
- **Host screen:** PhnLnk must never use full-screen call intents, setTurnScreenOn, or wake locks to activate a host display. Android or the phone manufacturer may independently wake it for a cellular call.

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
| 2026-11-07 | No Shizuku, root or debugging | User expressly rejected complex privileged setup |
| 2026-11-07 | No default Phone app role | User requires retaining stock dialer |
| 2026-11-07 | Maintain persistent repo instructions | Prevent recurring regressions during rapid prototyping |
| 2026-11-07 | Signed GitHub releases only | Stop shipping APKs with incompatible, temporary certificates |
| 2026-11-07 | Keep all incomplete audio routes clearly labeled | Avoid false promises to users |

## Maintenance rule

This file is meant to persist across future work. Coding agents should read `AGENTS.md` and this file first, update the decision log when the owner changes requirements, and never override the existing contract without explicit new user direction. GitHub Actions checks guard against a subset of known regressions; they do not replace human review or hardware testing.
