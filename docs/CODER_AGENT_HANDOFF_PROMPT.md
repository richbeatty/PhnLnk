# PhnLnk: handoff prompt for an expert Android, telephony and distributed-systems coding agent

Copy the text below into a competent coding agent with access to the GitHub repository, Android platform source, real build and testing environments. The agent must not conflate compiled source with device audio compatibility, and must record all actual changes in the repository.

## Mission

You are the principal Android telephony, audio, Bluetooth, networking and security engineer for **PhnLnk**, GitHub: https://github.com/richbeatty/PhnLnk.

Build a polished, highly reliable Android application that lets a user control and **genuinely hear and speak on cellular calls** placed using an existing AT&T SIM in a **Minimal Phone MP01 running Android 14**, from a second existing Android phone/tablet. This must work **on different networks**, including when the host phone is at home on Wi-Fi and the companion is elsewhere using mobile data or unrelated Wi-Fi. Preserve the original phone number, caller ID, SIM, carrier, stock Phone app and ordinary calling behavior. Do not require a third device, PC, gateway, headset, new SIM, number porting, carrier switch, root, Shizuku or ongoing manual debugging.

The user has explicitly allowed one optional, guided, Android-approved Wireless Debugging pairing on the host for audio experiments. This is **not** permission to auto-enable Developer options or silently grant ADB privileges. Android may turn Wireless Debugging off and the app cannot reliably turn it back on without privileged access. Basic dialing, answer and hangup must remain available without debugging. The user would consider default Phone app role only if it materially provides *verified* remote two-way audio and a genuinely improved dialer; default role alone does not allow privileged cellular PCM access.

The owner has repeatedly received experimental builds and now explicitly refuses further speculative device testing. Do not ask them to test another APK until you have exhausted static review, existing external research, automated reproducible tests and independent device compatibility evidence. Do not promise a hardware-dependent feature without evidence.

## Verified or reported facts

1. PhnLnk is a native Android Gradle project with applicationId `com.richbeatty.phnlnk`, targetSdk 34, minSdk 30. Main sources are under `app/src/main/java/com/richbeatty/phnlnk`; shell helper is under `shellprobe`.
2. The host can use Android Telecom to request outgoing dialing using the existing SIM. The user previously reported outgoing dial control working. Host call state, answer/end and contacts/alerts have implementations, but not every path has physical-device confirmation.
3. Earlier user results were `Connecting to Android`, `Connection failed: Stream closed`, then `Helper ready. Call audio not verified`. The local shell helper connection eventually succeeded.
4. The initial `VOICE_CALL` source test reported `SILENT_OR_MUTED`. This is a real user-reported result, not proof that every Android capture API is blocked.
5. A later user error was `EOFException` during the experimental voice implementation; its exact stack trace and phase were not supplied. Source review identified at least two potential early-close bugs. See `docs/EOF_INCIDENT_2026-11-08.md`.
6. v0.9.1 implemented an experimental two-way PCM stream: shell capture/injection backends; host TCP 47322 voice relay; HMAC-SHA256 paired-device proof; directional AES-256-GCM 640-byte PCM frames; companion foreground microphone/speaker; mute/route controls; provisional in-band DTMF.
7. The project uses an optional shell UID 2000 helper launched through local ADB. The current helper tries downlink `REDIRECT`, `VOICE_DOWNLINK` and `VOICE_CALL`, and uplink `REDIRECT` or an AudioTrack directed toward telephony. These methods may initialize yet still convey silence or not reach the carrier.
8. From 0.9.3 the call-control API uses pinned HTTPS on TCP **47321**, with QR or explicit full-fingerprint verification and saved-token migration. The audio socket TCP 47322 has authenticated AES-GCM media. The shell socket binds only to 127.0.0.1:47327. Do NOT expose the HTTP server directly to the public internet.
9. CI previously compiled the helper and Android app, but compilation and successful cryptographic transport are **not** proof of MP01 telephony audio. v0.9.2 adds a pure JVM protocol test harness and explicit EOF-stage handling. 0.9.3 expands production protocol/relay tests, adds TLS tests and repairs the helper Looper bootstrap and relay error-before-close. Verify current `main` build and tests before concluding anything.
10. The app must keep its **permanent Android release signing certificate**. Increase versionCode on every installable release and never publish a debug-signed APK as a normal upgrade. Private signing materials must never be committed to GitHub.
11. GitHub includes `MAINTAINER.md`, `AGENTS.md`, `CHANGELOG.md`, `docs/INDEX.md` and extensive setup, security, architecture, protocol, backend and troubleshooting documentation. Read these before changing code. They distinguish implementation from verified audio.

## Primary problem 1: obtain genuine cellular downlink and uplink on the MP01

Research Android 14 MediaTek device audio policy, Audio HAL, telephony routing, hidden Android AudioManager call-downlink extraction and call-uplink injection, VOICE_CALL and VOICE_DOWNLINK source restrictions, shell UID 2000 privileges and attribution, AudioPolicy mix registration, audio playback capture, Bluetooth HFP and authorized alternatives.

Use actual AOSP sources, Android API restrictions, credible open-source projects, ROM differences and real device reports. Confirm whether each method is public, hidden, `@SystemApi`, privileged, signature-only or possible from the approved shell helper. Do not assume root, system-app privileges or changing the default Phone role. Examine call-audio gateways such as `sparkcyf/android-voice-sip-agent`, JemRec, scrcpy audio forwarding, Android audio redirection internals and any credible MP01/Helio G99 examples. Treat success on an unrelated device as weak evidence.

Identify why the initial VOICE_CALL source returned silence. Analyze which current backend will produce true **far-end cellular speech**, and which will actually inject companion speech to the carrier. A local AudioRecord buffer containing nonzero samples is not sufficient. An AudioTrack accepting a write is not sufficient. If the firmware prevents the capability under user-approved privileges, state this explicitly rather than invent a workaround.

Investigate **Windows Phone Link / Link to Windows** as a reference. Microsoft's calling feature requires nearby Bluetooth, likely leveraging a hands-free profile rather than ordinary cellular PCM capture. Android's `BluetoothHeadsetClient` HFP client profile is a hidden/system API with `BLUETOOTH_PRIVILEGED` requirements. Determine whether stock Android can act as a hands-free client under the user's constraints. A PC-side Bluetooth hands-free gateway near the host is not acceptable because it adds a third device. Bluetooth alone cannot solve calls across separate networks. Do not confuse Phone Link's other Wi-Fi/cloud sync features with its nearby Bluetooth call audio path.

## Primary problem 2: secure cross-network availability

The user wants the host left at home and the companion available on other Wi-Fi networks or mobile data, including with NAT and CGNAT.

Implement and compare a staged plan:

- **Short-term validated path:** two existing Android devices joined through an independently configured authenticated private overlay VPN, such as Tailscale or another WireGuard-based private network, with stable host discovery and encrypted transport for the **entire control plane**, not only the audio. Do not claim PhnLnk currently installs the VPN or provides remote reachability.
- **Long-term one-app experience:** secure rendezvous and device identity, end-to-end encrypted control and voice, NAT traversal, WebRTC or an audited realtime transport, Opus encoding, jitter buffer, authenticated TURN relay when direct connectivity fails, FCM or similarly suitable incoming-call signaling, reconnect/roaming, Android background and foreground service lifecycle, and denial-of-service resistance.

Never port forward the host listeners or publish unauthenticated APIs. The remote relay must be a cloud/network service if needed, not extra hardware at the user's home. Document any hosting cost and cloud/privacy implications clearly.

Do not rewrite all transport code merely because a different protocol is trendy. First prove the host audio route. A working secure UDP stream that receives digital zeros is a failure of the user goal.

## Engineering tasks

1. Audit the actual repository head, release signing process, dependency versions, Android permissions, service lifecycle and current documentation. Identify concrete bugs, distinguish them from hypotheses, and keep a prioritized issue register.
2. Reproduce and harden `EOFException` paths. Ensure shell helper sends one explicit startup error when possible, never inserts text into active raw PCM, tracks connection stages, and uses bounded startup grace. The companion should show actionable sanitized failures, not bare Java exceptions.
3. Expand automated tests: actual TCP/HMAC/AES-GCM handshake, correct framing, wrong keys, partial reads, closed sockets, replay and tampering, concurrency, rekey assumptions, forged startup error frames and service-state cleanup. Add instrumented Android tests when feasible.
4. Revisit raw TCP PCM design for latency, buffering, backpressure, speaker and microphone routing, echo cancellation, noise suppression, audio focus and power management. A 20 ms frame over TCP is an engineering preview, not a production media stack.
5. Validate the implemented pinned HTTPS control plane and explicit saved-token migration on Android; do not reintroduce plaintext fallback or automatic certificate acceptance.
6. Implement stable same-Wi-Fi and **different-network** connection discovery with private VPN as an achievable first step, then propose a first-party relay architecture. Verify that mobile-data use does not rely on Wi-Fi IP addresses or router configuration.
7. Preserve incoming call notifications, real answer and hangup, contacts, favorites, recent call requests and in-call keypad behavior. Track Android Telecom authorization differences without forcing default Phone role.
8. Make the UX feel like a single polished product: simple pairing, consistent visual language, explicit host/helper/network/voice statuses, automatic recovery when Android permits it, minimal technical options unless diagnosing a failure, no repeated reinstall/pair loop.
9. Maintain the existing package ID and permanent signing certificate. Ensure GitHub Actions checks helper build, media tests, Android compile, version bump and artifact integrity. Never leak signing credentials.
10. Create and update comprehensive GitHub documentation. Read `docs/INDEX.md` and maintain architecture, permissions, ports, wire protocol, threat model, user setup, failures, release procedure and actual evidence.

## Acceptance gates, no shortcuts

- Two existing physical Android devices, no third device, with unchanged AT&T SIM and caller ID.
- Host on home Wi-Fi; companion first on the same private Wi-Fi, then on cellular/mobile data and another unrelated Wi-Fi network.
- Outbound and inbound carrier calls are controlled correctly, with ringing, answer, active state, reliable hangup and safe resource cleanup.
- Far-end caller's understandable speech is heard **through the companion**, not the host alone.
- Far-end caller can hear understandable speech **from the companion microphone**.
- Both directions function simultaneously for a real conversation with acceptable delay and no unintended echo.
- The original stock Phone app, host hardware, number and carrier remain usable, including after PhnLnk fails.
- Media and control commands are authenticated and encrypted across different networks; no raw public HTTP or untrusted pairing.
- Paired-device revocation immediately stops control and media access; sessions fail safely.
- A reboot, screen lock, Android battery restrictions and network changes are handled within Android's actual permission model.
- Existing permanently signed APK updates install over one another without clearing pairing.
- Automated tests and CI pass. Separately, required audio gates are evidenced on the actual target device and never inferred from build success.

## Deliverables and reporting format

Produce (a) an audited prioritized blocker list with code references and verified external sources, (b) a technically credible audio-path recommendation with privilege and MP01 evidence, (c) a remote-network architecture document with threat model and migration steps, (d) actual reviewed code commits and automated tests in GitHub, (e) updated documentation and changelog, and (f) an honest release readiness report distinguishing **implemented**, **compiled**, **protocol-tested**, **device-tested** and **not working**.

Prioritize solving the user's actual remote-call audio goal over designing mock interfaces, publishing unsupported APKs or repeatedly asking the user to test. If the combination of hardware, Android policies and non-negotiable constraints is impossible, document precisely which requirement must change and provide the smallest justified alternatives. Do not promise success without evidence. Do not ask the owner to test another speculative APK.
