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

## Local HTTP control: significant remaining risk

`HttpBridge` listens on TCP 47321 with HTTP bearer authorization. It does **not** use TLS.

- Initial pairing sends the six-digit code and returns a token over HTTP.
- Later status and call-control requests send the bearer token over HTTP.
- A passive observer on an untrusted LAN can potentially capture the token.
- Anyone obtaining a valid token could attempt remote call-control requests.
- Browser origin checks and no-store headers do not replace transport encryption.

**Mitigation until a security upgrade:** Use only a trusted private Wi-Fi network or an independently provisioned authenticated private VPN. Never port forward TCP 47321 or 47322 to the internet. Treat the host's IP and service exposure as sensitive. An encrypted private VPN helps protect traffic in transit, but PhnLnk does not install or configure one.

**Unfinished security work:** Add authenticated TLS or a transport with mutual authentication for the control API, including initial pairing and token exchange. This is a prerequisite for safe broad internet deployment.

## Voice transport: authenticated and encrypted, but not audited

TCP 47322 uses HMAC-SHA256 proof-of-possession based on the existing paired token fingerprint, then derives independent client-to-host and host-to-client AES-256-GCM keys.

Implemented defenses:

- Host saves the SHA-256 token fingerprint, not the plaintext token.
- Both peers prove possession through nonces and HMAC.
- The raw token is not transmitted over the media socket.
- Each frame has an authenticated sequence and ciphertext.
- Directional keys and unique GCM nonce prefixes prevent cross-direction nonce reuse.
- Wrong tags and replayed or out-of-sequence packets are rejected.
- Only one active voice session is intended.
- Revoking all pairings closes the current media sockets.

Not yet included:

- Independent penetration testing, formal protocol verification or protocol version negotiation.
- Media session rate limits against unauthorized connection floods.
- Automatic media key rotation within a long-running session.
- Explicit authenticated per-frame end-of-session signaling.
- Full resource quota enforcement for simultaneous inbound connections.
- Secure control-plane pairing from a hostile local network.

AES on 47322 does not retroactively secure plaintext HTTP pairing on 47321.

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
| Compromised LAN | Media encrypted, control HTTP not | Token theft or unauthorized call-control actions |
| Stale Wireless Debugging pairing | User must manage Android authorization | Unknown debugging clients are dangerous |
| Leaked private signing key | Not solvable within app | Attacker could distribute trusted updates |

## Release signing

The Android signing keystore must never be committed to GitHub, printed in logs, included in artifacts or attached to public documentation. The permanently pinned certificate and monotonically increasing versionCode preserve update compatibility. Use encrypted GitHub Actions secrets only if the user explicitly configures release publishing.

## Security exit criteria for general use

Before marketing as an internet-ready calling app, require independently reviewed control-plane encryption, safe pairing, authorization rate limiting, reliable explicit logout/revocation, tested media confidentiality and integrity, device compatibility evidence, and an auditable incident-response process.

For the existing development builds, keep all limitations prominent. See [Voice protocol](VOICE_PROTOCOL.md) and [Release playbook](RELEASE_PLAYBOOK.md).
