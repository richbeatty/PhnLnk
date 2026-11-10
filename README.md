# PhnLnk

PhnLnk is an experimental Android companion for controlling calls from an existing cellular phone and attempting to use a second existing Android device for voice. The project preserves the host's original SIM and number. It is designed for a main phone and a companion, without a third gateway or default-Phone-app requirement.

**Current development version: 0.9.5, Android runtime reliability candidate.** See [reliability reset](docs/RELIABILITY_RESET_2026-11-10.md); new audio work is paused until actual Android TLS/pairing passes. MP01 two-way cellular speech remains unverified.

**Critical status:** PhnLnk's two-way encrypted media transport has been implemented, but **actual cellular audio capture and injection on the owner's Minimal Phone MP01 have not been verified**. The user previously reported the original call recording route as silent. Software tests and Android compilation cannot establish that the distant caller will hear and be heard. This is **not** a reliable calling replacement or emergency dialer.

## Documentation

Start with the complete [documentation index](docs/INDEX.md). It links to the [user setup guide](docs/SETUP_GUIDE.md), [architecture](docs/ARCHITECTURE.md), [voice protocol](docs/VOICE_PROTOCOL.md), [audio backends](docs/AUDIO_BACKENDS.md), [API reference](docs/API_REFERENCE.md), [troubleshooting](docs/TROUBLESHOOTING.md), [security review](docs/SECURITY.md), [test plan](docs/TEST_PLAN.md), [release playbook](docs/RELEASE_PLAYBOOK.md), [maintainer workflow](docs/MAINTAINER_WORKFLOW.md), [known limitations](docs/KNOWN_LIMITATIONS.md), [design decisions](docs/DECISIONS.md) and the [November 2026 EOF analysis](docs/EOF_INCIDENT_2026-11-08.md).

Also read [MAINTAINER.md](MAINTAINER.md) before modifying software or promising a user-facing fix.

## What is implemented

- Main phone retains its AT&T SIM, carrier number and stock Android Phone app.
- Companion can pair, request dialing, request answering or ending a call, browse its own contacts, use favorites and recent call requests, and display incoming alerts while connected.
- Optional guided Wireless Debugging pairing on the main phone, with saved ADB identity and best-effort helper restart while Android keeps debugging available.
- A shell UID 2000 helper that can attempt several cellular downlink and uplink audio routes, with explicit diagnostics and a loopback-only PCM socket.
- A separate voice socket authenticated with the existing paired-device token fingerprint, using directional AES-256-GCM audio frames.
- Companion microphone foreground service, speaker or earpiece route, mute, and experimental in-band DTMF tone generation.
- A stage-aware error path for host helper initialization, authorization, connection closing, truncated media and timeouts.
- Automated tests of production voice protocol, relay and TLS code over real loopback sockets, plus an offline vendor-policy inspector.
- A prepared/pumped framework Looper retained in helper V4, staged startup errors, bounded authentication workers and improved cancellation. V4 adds an optional native 48 kHz stereo uplink with a tested PCM adapter; caller audibility remains unverified.

The app remains a development preview because the actual MP01 telephony audio HAL is the unresolved integration point. Read the [MP01 source investigation](docs/MP01_AUDIO_FEASIBILITY.md) and [engineering audit](docs/ENGINEERING_AUDIT.md): public manufacturer sources expose relevant routes and native format constraints, but are not proof of two-way speech on the installed firmware.

## Network boundaries

| Port | Protocol | Use |
| --- | --- | --- |
| 47321 | HTTPS with verified host certificate pin | Pairing and call control |
| 47322 | HMAC-authenticated AES-GCM TCP | Experimental companion voice |
| 127.0.0.1:47327 | Local secret-authenticated TCP | Shell audio helper |

**Never expose these listeners by port forwarding to the public internet.** Pairing and call control now use pinned HTTPS, including initial token exchange. Existing Android companions retain their tokens and verify the host fingerprint once; see [control migration](docs/CONTROL_SECURITY.md). Use only trusted private Wi-Fi or an independently provisioned private encrypted VPN. PhnLnk does not create an internet relay or provision a VPN.

## Build and test

With Java 17, Android API 35 SDK/build tools and Gradle available:

```bash
python3 tools/maintainer_check.py
bash tools/test_voice_protocol.sh
bash shellprobe/build.sh
gradle --no-daemon :app:assembleDebug
gradle --no-daemon :app:assembleRelease
```

The shell build packages `assets/phnlnk-probe.jar` into the APK. GitHub Actions executes the source checks, voice protocol suite, helper build and Android debug/release builds.

**What CI proves:** Source invariants, Java protocol behavior, bytecode compilation, and generated artifacts. **What CI does not prove:** MP01 cellular downlink, distant caller hearing an injected uplink, connection reliability when locked, remote private VPN setup, or successful real two-way speech.

## Release signing and updates

The application ID is `com.richbeatty.phnlnk`. Installable updates require increasing versionCode and the same permanent signing certificate. Debug or unsigned CI artifacts must not be presented as normal installable upgrades. Signing material must never be committed or included in uploaded artifacts. The manual GitHub release workflow requires the owner's private signing credentials configured as encrypted Actions secrets.

See the [release playbook](docs/RELEASE_PLAYBOOK.md) for the full procedure and [changelog](CHANGELOG.md) for versions.

## Product constraints

- No extra physical gateway, SIM, number porting, root or Shizuku.
- No forced default Phone role.
- Optional one-time Android-approved Wireless Debugging pairing only for the audio experiment; not required for normal call control.
- No silent activation of debugging or bypass of Android approval.
- No claim of working cellular voice until verified on the actual MP01 in both directions.
- No emergency calling use.

The user has asked not to be repeatedly sent speculative builds to test. Maintain accurate status and fix independently demonstrable software regressions first.

## License and contributions

See [LICENSE](LICENSE). Read [AGENTS.md](AGENTS.md), [MAINTAINER.md](MAINTAINER.md), [docs/MAINTAINER_WORKFLOW.md](docs/MAINTAINER_WORKFLOW.md) and the [security review](docs/SECURITY.md) before contributions that change call control, audio, pairing, cryptography or release signing.
