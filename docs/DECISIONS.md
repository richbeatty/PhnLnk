# Architecture decision records

These are durable explanations of decisions that affect the owner's requirements. Do not undo them casually in a later troubleshooting cycle.

## ADR 001: Keep the existing cellular SIM and stock dialer

**Decision:** Use the existing AT&T SIM host as the cellular endpoint. Send authorized dial, answer and end requests through Android's existing APIs. Do not require the default Phone role just to get a prettier dialer.

**Reason:** The user wants their original number, SIM and carrier with no third device. Becoming a default dialer does not grant ordinary third-party code the restricted cellular downlink or uplink audio permissions.

**Consequence:** Advanced DTMF and media require separate investigation. Do not promise a default-role change solves telephony audio.

## ADR 002: Optional user-approved self-ADB, not silent privilege escalation

**Decision:** Support a guided one-time Wireless Debugging pairing for experimental host audio access, with saved identity and best-effort reconnect.

**Reason:** The user explicitly accepted this narrow trade-off after originally rejecting debugging. Android owns the developer setting and pairing approval.

**Consequence:** Wireless Debugging may be disabled by Android after restart. The app cannot turn it on silently. Basic call controls cannot require debugging.

## ADR 003: Keep privileged audio inside a loopback-only shell process

**Decision:** Launch a small shell UID 2000 helper via `app_process`, limit it to the phone's loopback interface and authenticate it with a local per-install secret.

**Reason:** Ordinary Android app microphone permission does not grant cellular downlink capture and uplink injection. Separate the experimental capability from normal app permissions.

**Consequence:** Firmware policy can still block audio. Helper readiness must not be presented as successful calling.

## ADR 004: Use persistent ADB shell commands

**Decision:** Use one marker-delimited `exec:sh` session per local Android ADB connection.

**Reason:** Earlier versions used short-lived shell streams and the user reported `Stream closed`. The chosen libadb version has an upstream documented close-race in short-lived streams.

**Consequence:** Preserve marker framing, bounded command waits, one fresh reconnect and safe cleanup. Do not reintroduce the same failure by using independent `shell:` streams without a verified dependency fix.

## ADR 005: Encrypt media separately from call-control HTTP

**Decision:** The experiment uses a dedicated TCP 47322 media listener with HMAC authentication derived from paired-device token fingerprints and AES-256-GCM audio frames.

**Reason:** Audio PCM is especially sensitive. Authentication and confidentiality are necessary even on a private LAN.

**Consequence at 0.9.0:** Media encryption did not protect plaintext pairing/control. Superseded by ADR 010: 0.9.3 adds pinned HTTPS on 47321, while public port forwarding remains prohibited.

## ADR 006: Preserve a strict distinction between implemented and verified

**Decision:** Classify a source feature as implemented when code exists and compiled, protocol verified when automated tests exercise its real wire implementation, and working on MP01 only after physical far-end speech tests.

**Reason:** The user reported the initial capture as silent. Other Android devices' successes do not establish MP01 compatibility. Repeated speculative install requests erode trust.

**Consequence:** User-facing UI, release notes, README, status fields and docs must not claim full two-way audio until the evidence exists.

## ADR 007: Source-level recovery from raw EOF

**Decision:** Map an early socket close to a named phase, send an authenticated startup error when possible, keep longer bounded setup grace intervals, and expose short host diagnostics to an authorized companion.

**Reason:** The original shell helper could throw before responding to the host's `readUTF()`; the companion could then see only `EOFException`. A nine-second shell PCM input timeout could also expire while the companion was initializing its microphone.

**Consequence:** Maintain regression tests for early handshake EOF, media EOF and no unexpected text after PCM readiness. A staged message identifies where to investigate, but does not guarantee the Android HAL will work.

## ADR 008: Permanent update signing is part of functionality

**Decision:** Keep a constant application ID and permanent release signing identity. Version codes increase for every installable release.

**Reason:** Android will not normally install a differently signed APK over the existing app, and uninstalling can erase pairings. The user specifically requested updates that work without resets.

**Consequence:** GitHub CI must never publish a debug-signed artifact as if it were a normal update. Signing secrets do not go into source code or general logs.

## ADR 009: No automatic public relay without a privacy design

**Decision:** Support only trusted private networks or an independently configured authenticated VPN for the existing prototype. Do not expose raw host ports publicly.

**Reason at the original decision:** Pairing and control were plaintext. With pinned HTTPS in 0.9.3 the remaining public-service and lifecycle controls are still incomplete, so a public relay remains deferred.

**Consequence:** Away-from-home convenience is a product requirement not yet solved by the current prototype. Secure transport design and test work remain open.

## ADR 010: Documentation is a release artifact

**Decision:** Maintain architecture, API, crypto, security, troubleshooting, user setup, tests, limitations, release procedures and decisions in GitHub. Make docs part of maintainer regression checks.

**Reason:** Previous iterations introduced overlapping prototypes and inaccurate descriptions. A shared written contract helps future maintainers avoid reintroducing known errors.

**Consequence:** Code changes should update their corresponding documents. No undocumented permission escalation, public network listener, protocol change or signing process is acceptable.

See [Maintainer workflow](MAINTAINER_WORKFLOW.md) for update policy.


## ADR 010: Require pinned HTTPS without discarding saved Android pairing

**Decision:** 0.9.3 serves HTTPS on the existing 47321 port, with a persistent AndroidKeyStore host certificate and explicit QR/full-fingerprint verification. Existing tokens migrate only after verification. No plaintext fallback.

**Reason:** Encrypt initial code/token exchange and subsequent control without trusting arbitrary LAN certificates or silently breaking pairing identity. The APK signing key is unrelated and unchanged.

**Consequence:** Both apps must update. Existing Android companions verify once; background polling fails closed until then. Browser origins change and may need re-pairing. Native TLS helpers are JVM tested; AndroidKeyStore and migration UI still need physical-device acceptance. See [control security](CONTROL_SECURITY.md).
