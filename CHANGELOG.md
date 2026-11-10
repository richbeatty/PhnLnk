# Changelog

## Unreleased, resumed MP01 investigation (November 10, 2026)

- Continued from main `198710f` without changing Android runtime code, version 0.9.3/code 17, pairing storage, TLS pins, V3 helper, media protocol or signing configuration. No APK distribution.
- Re-fetched both pinned manufacturer policies and verified their SHA-256 hashes against the prior audit. Added a native-format comparison to the offline inspector and three regression cases, including replay of the preserved MP01 policy evidence. Missing/dynamic profiles remain unknown; distinct profiles cannot be combined into invented support.
- Rechecked Android 14 call-mode, explicit Telephony Tx selection, INCALL_MUSIC and permission enforcement. Documented the distinction between mix-port and device-port formats and why neither 16 kHz failure nor 48 kHz success follows from policy alone.
- Repaired a reproduced TLS negative-test race: correct client certificate rejection may cause server-side SocketException during TLS 1.3 Finished. Client rejection and application-data assertions remain mandatory; production trust code is unchanged.
- See `docs/ENGINEERING_STATUS_2026-11-10.md` for results and remaining evidence gates.

## v0.9.3, verified control identity, helper bootstrap and production relay tests

- Replaced plaintext control/pairing with HTTPS on 47321, a persistent AndroidKeyStore host certificate, QR/full-fingerprint verification and fail-closed pin checks. Saved Android tokens migrate explicitly without uninstalling or new ADB pairing; both apps must update. Browser HTTPS trust/origin changes are documented.
- Fixed helper ActivityThread/Handler startup using a prepared and pumped Looper and cached shell context. Helper V3 replaces older processes while retaining approved Android identity.
- Fixed relay startup errors being lost when try-with-resources closed the remote socket before the error send.
- Serialized media sequence allocation/encryption/write, bounded authentication workers, acquired single-session ownership after authorization, and tracked pending sockets for revocation and shutdown.
- Added bounded HTTPS workers/queue and absolute socket deadlines. Diagnostics and streaming share the helper audio-operation gate.
- Improved companion connect cancellation, blocked overlapping session scheduling, stopped blocking audio devices on shutdown and guarded API 31 routing for Android 11. Android runtime validation remains open.
- Expanded actual production protocol tests to 22, added 9 production relay cases and 5 TLS cases; added a four-test offline audio-policy inspector.
- Investigated pinned Android 14 sources and Minimal's published MP01/MT6789 device policy, including fixed-48k/native format constraints and differing policy variants; documented external MediaTek direct-mode reports, HFP restrictions, private VPN setup and WebRTC requirements.
- Kept applicationId and release signing configuration; versionCode 17. No permanent key was accessed, no release published, no speculative APK requested. **Actual MP01 two-way speech, AndroidKeyStore migration and physical VPN/background behavior remain unverified.**

## v0.9.2, EOF diagnostics, regression tests and complete developer documentation

- Fixed source-level voice helper early close during Android audio initialization by sending an explicit pre-READY `ERROR:AUDIO_INIT` when possible.
- Protected established raw PCM streams from accidental UTF error messages after STREAM_READY.
- Increased bounded startup/read grace periods where the companion was still initializing its microphone.
- Named handshake, authorization, helper startup and encrypted media EOF or timeout stages.
- Exposed sanitized host voice stage/error in the authenticated status payload for companion diagnostics.
- Kept companion voice failure messages visible during active calls and fixed reused dialer view parenting.
- Added real TCP loopback HMAC/AES voice protocol regression tests, including correct duplex, early close, invalid pairing, tampering and sequence replay.
- Added the protocol suite to GitHub Android CI, ahead of release compilation.
- Published a complete GitHub documentation hub: architecture, protocol, audio backends, API, setup, security, troubleshooting, testing, release, decisions and known limitations.
- Added a research-led analysis of Windows Phone Link's nearby Bluetooth hands-free audio and a separate-network strategy including VPN and secure future internet relay.
- Added a detailed copyable prompt for a coding agent to pursue actual remote two-way carrier calling without promising unverified MP01 compatibility.
- Still not verified to transmit genuine cellular speech on the physical MP01; do not treat this as a completed user-ready voice release.


## v0.9.1, clear voice setup and limitations

- Clarified the call screen's voice controls and connection states so they match the implemented experimental voice stream.
- Updated the main phone's audio guidance without claiming device compatibility or making PhnLnk the default Phone app.
- Reconciled outdated README language with the v0.9 voice transport and private-network constraints.
- Kept version increments and permanent signing certificate continuity.


## v0.9.0, encrypted two-way voice engineering preview

- Implemented shell-side full-duplex 16 kHz PCM bridge for an active cellular call, activated only after the user has approved local Wireless Debugging pairing.
- Tried additional downlink routes: Android call-downlink extraction API, VOICE_DOWNLINK, and VOICE_CALL, with digital-silence fallback while streaming.
- Tried uplink via call-injection API or explicit TYPE_TELEPHONY audio device. User may override routes on the host, since vendors differ.
- Implemented paired-device challenge-response voice authentication with HMAC-SHA256 and AES-256-GCM media frames, separate per-direction keys and monotonically increasing sequence numbers.
- New host LAN voice listener on port 47322, independent of ordinary HTTP call controls. The shell helper remains on loopback only.
- Companion microphone foreground service, encrypted uplink/downlink, speaker/earpiece control, mute, and on-screen voice-session status.
- Added in-band DTMF tone fallback when the stock dialer cannot provide telecom DTMF controls. Detection by IVRs is not guaranteed.
- Preserved pairing, prevented concurrent voice connections, and terminated active media if pairings are revoked or the companion disconnects.
- Supported private-network VPN addresses in addition to local RFC1918 Wi-Fi addresses. A VPN must be separately configured. Do NOT forward ports to the public Internet.
- No new default Phone app role, root, Shizuku, number porting, SIM or carrier change, or additional hardware.
- This is an engineering preview, NOT verified working cellular voice on the user's exact MP01. Android can still yield silent call audio or an inaudible uplink. Incoming/outgoing, background, roaming, and WAN behavior need hardware tests.


## v0.8.2, Android Stream closed failure

- Tracked the reported error to a documented libadb-android 3.1.1 wireless ADB stream-close defect (GitHub issue #34, PR #35).
- Replaced successive short-lived "shell:" commands with one long-lived "exec:sh" shell session per ADB connection and marker-delimited output.
- Verified Android is providing shell UID 2000 before staging the local helper.
- Added one fresh ADB-connection retry if a stream closes unexpectedly, with precise failure-stage reporting.
- Preserved the bounded 14-second command timeout; cleanup happens off the caller's thread.
- Preserved paired Android authorization, manual connection port and normal call controls.
- These are host-side transport fixes, not an implemented remote cellular audio bridge.
- No on-device MP01 verification yet.


## v0.8.1, fix connection status and local ADB discovery

- Removed an overly strict local-interface filter that could discard Android's own mDNS advertised debugging port.
- Kept every actual ADB connection strictly on 127.0.0.1.
- Added detailed connection phases: finding port, connecting, copying helper, starting helper, waiting for health check.
- Added 14-second timeout to ADB shell commands so the status cannot hang indefinitely on a stopped command.
- Added a warning after 45 seconds without connection progress.
- Added a manual connection-port recovery field, using the number on Android's Wireless Debugging screen, separate from the six-digit pairing port.
- Added safe local helper crash log diagnostics if the launch exits early.
- Added regression checks for these fixes.
- Still requires real MP01 testing of audio compatibility. No two-way remote voice claims.


## v0.8.0, guided audio access and reconnection

- Added opt-in guided Wireless Debugging setup on the main phone with Android pairing-code notification.
- Reused saved Android pairing identity, and added foreground background retry service with visible status.
- Automatic retry while Wireless Debugging is on, and best-effort recovery after reboot or reopening the host app.
- Added a stop switch to disable automatic reconnect and request local helper shutdown. Revoking Android's ADB authorization remains a user action in Developer options.
- Audio tests remain user-consented, calculate levels only, and never save call recordings.
- Kept original Phone app and everyday call controls independent of Android debugging.
- Updated maintainer instructions for the owner's newly approved optional one-time setup.
- Does not add a real two-way audio stream to the companion device.


## v0.7.0, interface refresh

- New reusable native visual style with muted green surfaces, readable typography, clear primary actions and consistent controls, on host, companion, and role selection.
- Companion reorganized into a focused keypad, contacts, favorites, and recent call-request views.
- Live call status and answer/end controls are now visibly separated from number entry.
- Saved pairing, incoming-call alerts, host Wi-Fi address changes, and disconnection are organized under Connection settings.
- Main phone has a simpler high-contrast status and QR pairing view.
- Corrected contact search rendering to keep the search field visible while refreshing results.
- Preserved non-default-dialer calling and the explicit limitation that voice audio remains on the host phone.
- No new remote cellular audio capability is claimed; Android default Phone role still does not grant modem audio capture.


## Unreleased, v0.6.1 maintenance changes

- Formalized permanent requirements in `MAINTAINER.md` and `AGENTS.md`.
- Kept the host's regular Phone app unchanged; removed requests to set PhnLnk as the default Phone app.
- Removed Wireless Debugging from the host's main setup screen. The v0.6 experimental code remains in Git history for reference but is not a normal supported calling route.
- Added automatic regression checks for app identity, device-neutral branding, unsupported login requirements, temporary signing, and release gates.
- Split ordinary debug build artifacts from signed GitHub Releases.
- Refused unsigned or temporary-debug-signed GitHub Releases, pending the owner's private Actions signing secrets.
- No claims of native cellular audio relay. No changes to the carrier, SIM or number.

## v0.6.0, experimental audio diagnostics

- Compiled embedded self-ADB shell audio diagnostics for compatible devices using optional Wireless Debugging.
- Diagnostic audio tests did not establish working remote voice.
- Distributed with runner-debug signing certificate, which prevents updating different-signature versions in place.

## v0.5.0, hardware-assisted experiment

- Experimental SIP and Bluetooth gateway approach. Requires a third device and does not meet the permanent product contract.

## v0.4.0

- Optional default-dialer-based DTMF and call controls. Default-dialer requirement rejected.

## v0.3.0

- Android host and companion roles, native keypad, local contacts, favorites and call status.

## v0.2.x

- QR and short-code pairing, remembered local browser keys.

## v0.1.x

- Initial authenticated local-network call-control app.
