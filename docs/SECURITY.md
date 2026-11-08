# Security and privacy review

This document describes the present implementation, including incomplete protections. It is not a third-party audit or a claim that the app is safe for public internet exposure.

## Threat model

The host contains the user's actual SIM and can initiate or answer cellular calls. Its paired control token is therefore sensitive. The companion has a microphone and speaker. The optional shell helper has elevated capabilities relative to an ordinary application and must not become a network service.

Consider these attackers:

1. A passive observer on the same Wi-Fi during original pairing or call control.
2. An active LAN attacker able to scan or connect to open host ports.
3. Someone who obtains a saved companion pairing token or private app data.
4. An unauthorized Android ADB pairing on the main phone.
5. A malicious or malfunctioning application trying to impersonate an authenticated media peer.
6. Bugs in packet parsing, media concurrency or vendor Android services.

Current protection does not fully address every attacker.

## Pinned HTTPS control and pairing (0.9.3)

`HttpBridge` now serves TLS on TCP 47321. The host certificate is held in AndroidKeyStore, independently of APK signing. The native companion pins its complete SHA-256 certificate fingerprint, obtained from the physical host QR or an explicit all-groups comparison. Initial code/token exchange and subsequent bearer control are encrypted. There is no plaintext fallback.

Manual discovery obtains only an **untrusted candidate** over a TLS handshake, with no application data. It is trusted only after the user compares it with the intended host. The six-digit pairing code by itself does not prevent an active MITM. Do not silently accept a changed pin or weaken global CA trust. Native requests reject redirects and pin mismatches.

Existing Android pairings retain their tokens. They need one explicit certificate verification and an atomic address/pin migration before background requests can resume. Updating only one app does not bridge HTTP and HTTPS. Browser storage changes with the origin; browser trust requires separate fingerprint verification. See [control identity and migration](CONTROL_SECURITY.md).

**Exposure boundary:** still use a trusted private network or an independently configured authenticated VPN. Never port forward host or debugging ports. TLS fixes transport confidentiality, not all public abuse, lifecycle or availability risks. Worker limits and socket deadlines reduce resource exhaustion, but are not a public-service security review. Old tokens sent over HTTP before this update may already be compromised; explicit revocation and secure re-pairing are necessary if compromise is suspected.

**Unverified:** AndroidKeyStore runtime behavior, identity persistence on a signed app update, QR/manual UI behavior on physical devices, and independent penetration testing. The JVM TLS suite executes actual TLS primitives with test certificates, not AndroidKeyStore.

## Voice transport: authenticated and encrypted, but not audited

TCP 47322 uses HMAC-SHA256 proof-of-possession based on the existing paired token fingerprint, then derives independent client-to-host and host-to-client AES-256-GCM keys.

Implemented defenses:

- Host saves the SHA-256 token fingerprint, not the plaintext token.
- Both peers prove possession through nonces and HMAC.
- The raw token is not transmitted over the media socket.
- Each frame has an authenticated sequence and ciphertext.
- Directional keys and unique GCM nonce prefixes prevent cross-direction nonce reuse.
- Wrong tags and replayed or out-of-sequence packets are rejected.
- One authenticated voice owner; four bounded handshake workers with an absolute nine-second handshake deadline. Idle unauthenticated sockets do not reserve the owner.
- Revoking all pairings closes active and authenticating media sockets. The next control request must authorize again; an action already dispatched to Telecom cannot be recalled.

Not yet included:

- Independent penetration testing, formal protocol verification or protocol version negotiation.
- Per-peer rate limits and protection against repeated connection floods beyond bounded workers.
- Automatic media key rotation within a long-running session.
- Explicit authenticated per-frame end-of-session signaling.
- Public-service abuse protection and per-device quotas beyond the implemented worker/queue caps.
- Independent review of the implemented QR/manual certificate verification and trust migration.

Both ports now encrypt traffic. Upgrading cannot retroactively protect tokens exchanged by older plaintext releases. The stored token hash is an equivalent voice-authentication secret and must be protected even though it is not the raw bearer token.

## Local shell helper

TCP 47327 is restricted to 127.0.0.1 and expects a per-install random secret. The helper refuses startup unless Android reports shell UID 2000. PCM is transmitted between the helper and the host app without additional encryption because both endpoints reside on the main phone's loopback interface.

Important assumptions:

- Android's sandbox and private app storage protect the stored helper secret.
- A separate untrusted process does not have access to that secret.
- Wireless Debugging authorization remains controlled by Android and the user.
- The user does not pair unknown debugging clients and does not expose ADB to the LAN.

The app does not and must not silently enable debugging or grant its own authorization. Stored debugging identities can remain valid while Android has Wireless Debugging turned off. The user can revoke them in Android Developer options.

## Permission handling

The companion requests RECORD_AUDIO only when the user explicitly starts voice. A microphone foreground service supplies a persistent notification and a disconnect action. The user can revoke microphone permission in Android Settings. The normal Phone app remains the default dialer.

The host requests only permissions necessary for the implemented call control and optional setup. The privileged shell helper is separate from ordinary app permissions. Any new role or permission must be reflected in the manifest, setup guidance and MAINTAINER.md.

## Data retention

- The audio diagnostic computes level statistics; it does not intentionally store call recordings.
- The live voice bridge carries PCM in process memory and does not intentionally persist it or upload it to a cloud service.
- Companion contact browsing uses the companion device's contacts; this is not a host-side contact synchronization service.
- Paired-token hashes remain in host private app preferences until revoked or app data is cleared.
- Companion saved token remains in companion private app preferences until disconnect or app data reset.

Do not log or report bearer tokens, private shell keys, six-digit Android debugging codes, contacts or raw speech. Error logs should contain short stage names and sanitized exception types, not full authenticated request data.

## Attacker and failure scenarios

| Scenario | Current handling | Remaining risk |
| --- | --- | --- |
| Wrong media HMAC | Host rejects before authenticated media | Abuse of connection availability |
| Modified AES frame | GCM authentication fails and stream closes | No media resynchronization |
| Replay or wrong sequence | Rejected | Connection disruption |
| Revoked host pairing | Closes active sockets | Already captured plaintext token on Wi-Fi remains sensitive until revocation |
| Wrong Android shell key | Local helper rejects request | Local denial of service |
| Compromised LAN | Media encrypted; control/pairing use pinned HTTPS | Wrong manual fingerprint approval, previously stolen tokens, denial of service |
| Stale Wireless Debugging pairing | User must manage Android authorization | Unknown debugging clients are dangerous |
| Leaked private signing key | Not solvable within app | Attacker could distribute trusted updates |

## Release signing

The Android signing keystore must never be committed to GitHub, printed in logs, included in artifacts or attached to public documentation. The permanently pinned certificate and monotonically increasing versionCode preserve update compatibility. Use encrypted GitHub Actions secrets only if the user explicitly configures release publishing.

## Security exit criteria for general use

Before marketing as an internet-ready calling app, require independently reviewed control-plane encryption, safe pairing, authorization rate limiting, reliable explicit logout/revocation, tested media confidentiality and integrity, device compatibility evidence, and an auditable incident-response process.

For the existing development builds, keep all limitations prominent. See [Voice protocol](VOICE_PROTOCOL.md) and [Release playbook](RELEASE_PLAYBOOK.md).
