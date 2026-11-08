# System architecture

## Three independent capabilities

PhnLnk is designed for two Android devices. The host is the phone containing the existing AT&T SIM. The companion makes requests and can attempt to carry audio. The product must not move the SIM, port the number, require another device, or silently replace the normal Phone app.

1. **Call control:** Authenticated companion requests to the host's Android Telecom and Telephony APIs.
2. **Audio extraction and injection:** An explicitly approved Android shell UID 2000 helper tries firmware-dependent cellular audio methods.
3. **Voice transport:** A separately authenticated and encrypted media connection between the two app processes.

These three capabilities have different success criteria. The first two may fail independently of a perfect network and cryptographic implementation.

## Component inventory

| File or component | Runs on | Responsibility |
| --- | --- | --- |
| `MainActivity` | Either device | Select host or companion role |
| `HostActivity` | Host app | Pair device, manage host, display connection and audio setup |
| `GatewayService` | Host app | Foreground lifetime of control HTTP and voice listener |
| `HttpBridge` | Host app | Bearer-authenticated HTTP interface on port 47321 |
| `CallController` | Host app | Dial, answer, hangup, coarse call status and DTMF availability |
| `PairingManager` | Host app | Pairing code, saved token hashes, revocation and voice proof lookup |
| `HostAudioActivity` | Host app | Guided debugging setup, audio capture and injection diagnostics |
| `AudioPairService` | Host app | Six-digit Android ADB pairing notification |
| `AudioAccessService` | Host app | Opt-in reconnection while debugging is available |
| `HostAdb` | Host app | Loopback ADB shell commands via persistent exec:sh session |
| `AudioProbe` | Shell UID 2000 on host | Voice capture and uplink experiments, local PCM socket |
| `VoiceRelayServer` | Host app | TCP 47322 voice session, active-call check, shell bridge |
| `VoiceSecureTransport` | Both apps | HMAC challenge-response, AES-GCM and sequence checks |
| `ClientActivity` | Companion | Dialer, contacts, favorites, recents and voice controls |
| `RemoteClient` | Companion | Existing HTTP pairing, status and action requests |
| `CompanionService` | Companion | Polling and incoming-call notification |
| `VoiceSessionService` | Companion | Foreground mic, encrypted PCM and speaker output |
| `StartOnBootReceiver` | Either app | Best-effort service recovery after reboot |

## Network and process topology

```text
  Main Android phone with AT&T SIM
  +------------------------------------------------------+
  | Stock Phone app -> Android Telecom -> cellular call  |
  |                               |                      |
  |                       Vendor audio routing          |
  |                               |                      |
  |                 [Unverified on the MP01]              |
  |                               |                      |
  | Android shell UID 2000 / AudioProbe                   |
  | Local secret + PCM on 127.0.0.1:47327                |
  |                   |                 |                |
  | VoiceRelayServer / TCP 47322 / authenticated GCM     |
  | HttpBridge / TCP 47321 / authenticated HTTP, no TLS  |
  +-------------------+-----------------+----------------+
                      |                 |
                  Trusted LAN or independently set up VPN
                      |                 |
  +-------------------+-----------------+----------------+
  | Companion Android: RemoteClient and VoiceSession     |
  | Microphone -> AES voice -> host; host -> AES -> speaker|
  +------------------------------------------------------+
```

The shell helper never listens on the Wi-Fi or VPN interface. It is bound only to 127.0.0.1. The media and HTTP listeners are separate, and the existing HTTP listener is still plaintext. Never forward either port to the public internet.

## Ordinary call lifecycle

1. The companion pairs with the host using the host's temporary six-digit PhnLnk pairing code, which is separate from the Android debugging pairing code.
2. The companion sends `POST /api/dial` or `POST /api/answer` using the saved bearer token.
3. The host checks authorization and uses the stock Android Telecom service.
4. The host continues using its actual SIM and its default Phone app.
5. The companion polls `GET /api/status`, using the rough host states `idle`, `ringing` or `active`.
6. The companion can separately start an experimental voice session when the call is active.
7. Ending a call should close the voice socket and release microphone and speaker resources.

The user is not forced to make PhnLnk the default Phone app. Standard telecom DTMF requires privileges or role access unavailable to an arbitrary call controller. An experimental in-band tone fallback depends on the carrier and IVR.

## Optional audio helper startup

1. User grants the optional setup and enables Android Wireless Debugging in Settings.
2. Android issues a pairing code, and the user approves pairing. PhnLnk stores its own ADB identity.
3. `AudioAccessService` probes the helper; if missing, `HostAdb` attempts to connect to local adbd.
4. `HostAdb` uses the persistent `exec:sh` marker protocol because short-lived `shell:` streams were implicated in an upstream library close-race.
5. The bundled DEX helper is started with `app_process`, checks UID 2000 and opens its loopback server.
6. PING returning `READY:V2` proves shell helper availability only.

Android may turn Wireless Debugging off on reboot or network changes. A normal app cannot silently enable it again. The identity may remain paired while debugging is off.

## Voice media lifecycle

1. The user starts companion voice while the host has a real active cellular call.
2. A microphone foreground service starts on the companion with explicit RECORD_AUDIO permission.
3. The companion opens TCP 47322 on the host and performs HMAC pairing proof.
4. The host verifies active call state, access setting and local shell secret.
5. The host asks the shell helper for `STREAM:<capture>:<uplink>`.
6. The helper attempts its audio backends. It should report an explicit error if initialization fails, not silently close the socket.
7. After `STREAM_READY`, the host sends the companion a signed and encrypted READY control frame.
8. The companion creates microphone and speaker audio objects.
9. Bidirectional PCM moves in 640-byte frames, nominally every 20 ms.
10. A normal hangup, app disconnect, revoked token or fatal error closes the media path.

A backend can initialize but return digital zeros or send audio to the wrong hardware route. A connected encrypted stream is not the same as a verified conversation.

## Threads and failure domains

- TCP accept loops and worker pools handle sessions away from the UI thread.
- The companion has separate mic capture and speaker playback threads.
- The shell helper has separate downlink and uplink workers.
- Session cleanup must release AudioRecord, AudioTrack, the socket, audio effects and communication routing.
- Android can interrupt sessions through phone-call changes, screen-off restrictions, service termination, Wi-Fi or VPN changes, vendor HAL behavior, audio route changes or ADB shutdown.

See [Voice protocol](VOICE_PROTOCOL.md), [Audio backends](AUDIO_BACKENDS.md), [Security](SECURITY.md) and [Test plan](TEST_PLAN.md).
