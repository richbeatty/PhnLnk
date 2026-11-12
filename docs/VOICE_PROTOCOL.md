# Encrypted voice protocol

0.9.4 retains PHN1 and the 16 kHz mono PCM16 / 640-byte network and loopback frame layout. Local helper V4 additionally accepts `INJECT:TELEPHONY_48K` and `STREAM:<capture>:TELEPHONY_48K`; it converts uplink PCM to native 48 kHz stereo only at the AudioTrack boundary. PING returns READY:V4. Older V1/V2/V3 helpers must be replaced before using this option. No native-size PCM frame is transmitted on either socket. See [test candidate](TEST_BUILD_0.9.4.md).

The actual implementation is `app/src/main/java/com/richbeatty/phnlnk/VoiceSecureTransport.java`. This document describes the current private v1 wire format, not a reviewed public standard.

## Ports and security boundaries

| Port | Purpose | Security |
| --- | --- | --- |
| 47321 | Host control HTTPS | Verified certificate pin, then bearer authorization |
| 47322 | Companion to host media TCP | HMAC proof and AES-256-GCM encryption |
| 47327 | Host app to local shell helper | Loopback-only TCP and a 256-bit per-install secret |

Both implemented network transports now encrypt their traffic. Pairing and call control on 47321 use pinned HTTPS; media remains the independent v1 HMAC/AES-GCM protocol. See [control security](CONTROL_SECURITY.md) for trust verification and migration.

## Paired secret

Original pairing returns a 32-byte random token encoded as 43 characters of unpadded URL-safe Base64. The companion stores the token in its own private app preferences. The host stores its SHA-256 lowercase hexadecimal fingerprint.

For voice:

```text
K = SHA256(UTF8(raw Base64URL pairing token))
```

The host converts the stored 64-character hex fingerprint back into the same 32 bytes. The voice socket does not transmit the bearer token.

From 0.9.3 the initial token exchange requires pinned HTTPS. Tokens paired by older releases might previously have crossed plaintext HTTP; TLS does not revoke a previously stolen token. Explicit host revocation and new secure pairing are needed for a suspected compromise.

## Challenge and response handshake

All integer values are big-endian.

1. Host accepts TCP 47322 and writes the integer `0x50484E31`, ASCII `PHN1`.
2. Host writes 32 cryptographically random bytes as `serverNonce`.
3. Companion checks the magic and reads the nonce.
4. Companion creates a 16-byte `clientNonce`.
5. Companion writes its client nonce and 32-byte HMAC proof.
6. Host searches its authorized stored hashes using constant-time comparison.
7. If a saved key matches, host writes its own 32-byte server proof.
8. Companion verifies server proof with constant-time comparison.
9. Both derive two independent AES-256 keys.

For any label, the actual Java helper computes:

```text
HMAC_SHA256(
  K,
  ASCII("PhnLnk-v1-" + label + ":") || serverNonce || clientNonce
)
```

Defined labels: `client`, `server`, `client-to-host` and `host-to-client`.

A failed proof closes the connection without authenticated media. After early closure, the companion should display a staged authorization or network error, not a bare EOFException.

## Encrypted frames

Keys are derived by applying the HMAC helper to the direction labels. Each direction has its own sequence starting at zero. The first nonce is:

```text
nonce = 4-byte direction marker || 8-byte big-endian sequence
```

The direction markers are `0x434C4945` for client-to-host and `0x484F5354` for host-to-client.

Every frame contains:

| Field | Bytes | Rule |
| --- | --- | --- |
| Sequence | 8 | Big-endian; increases strictly by one |
| Ciphertext length | 2 | Big-endian unsigned, 656 for current format |
| Encrypted data | 656 | 640 bytes of plaintext and a 16-byte AES-GCM tag |

The sequence is included as AES-GCM associated authenticated data (AAD). A modified ciphertext, wrong key, incorrect sequence, replayed packet, incorrect length or partial frame terminates the session. There is no recovery by skipping bytes in the middle of a TCP stream.

## Startup control frame

The first authenticated frame from host to companion is a 640-byte plaintext buffer before encryption. It starts with a two-byte little-endian string length, then UTF-8 text beginning with `PHNLNK:READY:` or `PHNLNK:ERROR:`.

READY can report the selected shell capture and injection backend. It means those audio objects initialized, not that speech was actually captured or heard by the other party.

After READY, frames are raw PCM and are not self-describing. Do not add arbitrary text or another control frame to an established PCM stream without first versioning the protocol.

## Audio payload

- Sample rate: 16,000 Hz.
- Channels: one.
- Sample type: signed 16-bit little-endian.
- One frame: 320 samples, 640 PCM bytes, about 20 ms.
- Transport: TCP. Retransmission may add unacceptable latency or delay.
- No codec, jitter buffer, congestion control, packet loss concealment, audio-only heartbeat, voice activity detection or media rekey is implemented.

## Error policy and timeout stages

Companion phases: `CONNECT_HOST_PRIVATE_NETWORK`, `VOICE_AUTH_HANDSHAKE`, `WAIT_HOST_AUDIO_BACKEND`, `OPEN_COMPANION_AUDIO`, `VOICE_STREAMING`.

Host phases: `AUTH_HANDSHAKE`, `CHECK_ACTIVE_CALL`, `CONNECT_SHELL_HELPER`, `AWAIT_AUDIO_BACKEND`, `STREAMING`.

The host normally reports an error via an authenticated ERROR frame before the stream is ready. The main-phone status API exposes the last host failure stage and short diagnostic description for paired devices.

A host shell helper terminating before `STREAM_READY` is translated to `HELPER_CLOSED` instead of an unexplained EOF. A companion handshake closing before the nonce or server proof is translated to `VOICE_HANDSHAKE_CLOSED` with the phase. A truncated encrypted media frame becomes `VOICE_MEDIA_CLOSED`. These labels name the failing layer but do not prove the reason the firmware or network closed it.

Startup timeouts allow for multiple Android backend attempts and companion microphone initialization. They are bounded and are intentionally longer than the 20 ms frame interval. A peer that dies or stops sending eventually fails rather than hanging indefinitely.

## Automated validation

The pure JVM harness compiles the **production `VoiceSecureTransport.java` file** against narrowly scoped test stubs and uses real loopback TCP sockets. It verifies both encrypted directions, HMAC pairing rejection, early handshake EOF, media EOF, AES-GCM tampering and replay sequence checks.

```bash
bash tools/test_voice_protocol.sh
```

A pass verifies protocol mechanics only. It does not validate Android capture, injection, media latency, screen-off behavior, cellular voice quality, or the physical MP01.

Refer to [Security](SECURITY.md), [API reference](API_REFERENCE.md) and [EOF incident](EOF_INCIDENT_2026-11-08.md).
