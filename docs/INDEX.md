# PhnLnk documentation

This is the documentation home for PhnLnk. Read the implementation status before claiming a feature works on a particular phone.

**Current development version:** 0.9.3, experimental cellular voice transport. **Actual two-way cellular speech on the MP01 is not verified.** Compiling Android, connecting ADB, obtaining shell UID 2000, opening an audio route, and passing the encrypted voice protocol tests do not prove that the far-end person is heard.

## Guide to the repository

| Documentation | What to read it for |
| --- | --- |
| [Resumed engineering status](ENGINEERING_STATUS_2026-11-10.md) | Current source audit, native-format findings, reproduced test failure and remaining blockers |
| [User setup](SETUP_GUIDE.md) | Host and companion setup, pairing, call control, optional audio |
| [Architecture](ARCHITECTURE.md) | Components, lifecycle, threads, topology and trust boundaries |
| [Voice protocol](VOICE_PROTOCOL.md) | Byte-level handshake, crypto, audio framing and failure rules |
| [MP01 feasibility](MP01_AUDIO_FEASIBILITY.md) | AOSP, public manufacturer policy, comparable devices and remaining evidence |
| [Control security](CONTROL_SECURITY.md) | TLS identity, QR/manual verification and saved-token migration |
| [Engineering audit](ENGINEERING_AUDIT.md) | Prioritized defects, fixes, validation and open release gates |
| [Audio backends](AUDIO_BACKENDS.md) | Restricted Android telephony routes and alternatives |
| [API reference](API_REFERENCE.md) | Control endpoints and voice socket details |
| [Troubleshooting](TROUBLESHOOTING.md) | Failures including EOFException, silence and debugging reconnection |
| [Security](SECURITY.md) | Attacker assumptions, pinned HTTPS, migration and privacy |
| [Test plan](TEST_PLAN.md) | CI, protocol tests and real-phone acceptance criteria |
| [Release playbook](RELEASE_PLAYBOOK.md) | Build, signing, GitHub releases, update compatibility |
| [Maintainer workflow](MAINTAINER_WORKFLOW.md) | Contributor rules and prevention of previous regressions |
| [Known limitations](KNOWN_LIMITATIONS.md) | Feature matrix and remaining milestones |
| [Design decisions](DECISIONS.md) | Reasons behind current architecture |
| [EOF incident](EOF_INCIDENT_2026-11-08.md) | Source-level early-close and timeout analysis |
| [Audio test instructions](AUDIO_TESTING.md) | Live-call test steps and evidence |
| [Call research](CALL_RESEARCH_2026.md) | Alternatives and third-party Android research |
| [Product contract](../MAINTAINER.md) | The owner's product constraints |
| [Changelog](../CHANGELOG.md) | Versions and technical changes |

## Evidence labels

- **Implemented:** There is source code for a feature.
- **Build verified:** CI compiled the relevant source.
- **Protocol verified:** A controlled automated test ran the actual protocol implementation.
- **Device unverified:** No documented test on the owner's physical MP01 confirms the feature.
- **Hardware blocked or unknown:** Android privilege, vendor HAL or carrier behavior may prevent it.
- **Not implemented:** There is no complete implementation.

Avoid the single word "works" without naming the exact capability and test. A local helper answering PING is not an audio path. PCM sample counts are not proof that the signal comes from the far-end caller. A successful audio output API call is not proof the far-end caller heard it.

## Technical reference card

| Item | Current value |
| --- | --- |
| App package | `com.richbeatty.phnlnk` |
| Minimum Android API | 30 |
| Target Android API | 34 |
| Host control service | TCP 47321, HTTPS with verified host certificate pin |
| Encrypted voice transport | TCP 47322, HMAC pairing proof and AES-256-GCM |
| Local shell audio helper | TCP 127.0.0.1:47327, per-install secret |
| PCM | 16 kHz, mono, 16-bit signed little-endian |
| Frame | 320 samples, 640 bytes, about 20 milliseconds |
| Default Android Phone app | Unchanged |
| Android privileged setup | Optional, explicit Wireless Debugging pairing |
| Network | Trusted private Wi-Fi or separately provisioned encrypted VPN |
| Remote internet relay | Not included |
| Emergency calling | Use the stock Phone app |

## Documentation rule

Any source change to ports, authentication, voice packet layout, helper behavior, Android permissions, startup or shutdown, persistent pairing or user-facing feature claims must update the corresponding page here. Source-level maintainer checks catch some regressions, but are not a substitute for reviewing the prose against actual code.
