# Application API reference

This describes the current host control API in `HttpBridge.java` and the separate experimental audio connections.

## Host control listener

- Address: host phone private Wi-Fi or VPN interface.
- TCP port: **47321**.
- Transport: **HTTPS with a verified host certificate pin**.
- Authentication: PhnLnk bearer token in the `Authorization` header for routes other than pairing.
- Request output: JSON for API endpoints.
- This is **not safe as a public internet API**. Do not port forward it.

### `GET /`

Returns a bundled local web page. It is served over HTTPS. Self-signed browser trust must be verified separately; the native client pins the host. This view is not evidence that voice service works.

### `POST /api/pair`

Purpose: Pair the current companion using the PhnLnk host's six-digit pairing code. This is not the Android Wireless Debugging pairing code.

Request headers:

```http
Content-Type: application/json
X-PhnLnk: 1
```

Example request body:

```json
{"code":"123456"}
```

The code is only an example, not an issued valid code. A successful response returns a 43-character URL-safe bearer token. The host stores only a SHA-256 hash. It rate-limits pairing attempts and expires the temporary code.

From 0.9.3 the code and token cross only a verified TLS connection. QR pairing supplies the certificate pin; manual pairing requires a full-fingerprint comparison before sending the code. See [control security and migration](CONTROL_SECURITY.md). Keep the listener private.

### `GET /api/status`

Requires:

```http
Authorization: Bearer <saved_token>
```

Returns an object assembled by `CallController.status`. Important fields include:

| Field | Meaning |
| --- | --- |
| `host` | Whether the basic host gateway is running |
| `model`, `android`, `abi` | Main device identity and environment |
| `callPermission` | Host CALL_PHONE permission granted |
| `answerPermission` | Host ANSWER_PHONE_CALLS permission granted |
| `readPermission` | Host READ_PHONE_STATE permission granted |
| `hasTelephony` | Device reports telephony feature |
| `callState` | Approximate idle, ringing, active, permission needed or unknown |
| `dtmfReady` | In-call DTMF path available |
| `defaultDialerRequiredForDtmf` | True when DTMF access is missing |
| `audioRelayed` | Conservative false value; no claim of verified audible cellular speech |
| `voiceTransportActive` | Host voice socket has a session reserved |
| `voiceHostHelperReady` | Last local helper status reported ready |
| `voiceTransportSupported` | Voice transport implementation exists; **not a firmware compatibility result** |
| `voiceStage` | Most recent host voice startup or lifecycle phase |
| `voiceLastError` | Recent sanitized host voice error or absence of error |
| `voiceLastErrorAt` | Unix epoch milliseconds when that error was stored |
| `telephonyDeviceVisible` | Android enumerated a telephony audio device; not proof of routability |

Do not infer that `voiceTransportSupported` or `voiceHostHelperReady` means remote audio is functional.

### `POST /api/dial`

Requires authenticated bearer, `X-PhnLnk: 1` and JSON content type. Body:

```json
{"number":"+15555550123"}
```

The host validates a 10-digit NANP number and calls Android Telecom. No number transfer or new SIM is involved. Premium-rate 900 calls are blocked in the current implementation.

### `POST /api/answer`

Authenticated empty JSON request. Attempts to answer a currently ringing host cellular call using Android Telecom. Requires the host's permissions.

### `POST /api/end`

Authenticated empty JSON request. Asks Android Telecom to terminate the current host call. It can fail if Android refuses the request or the call no longer exists.

### `POST /api/dtmf`

Authenticated JSON request:

```json
{"digit":"5"}
```

Requires a single digit 0-9, `*` or `#` and the host's allowed in-call DTMF path. If unavailable, the companion has an experimental in-band PCM tone fallback only while its voice stream is active. Carrier IVR detection is not guaranteed.

## Errors and HTTP hardening

- 401: unauthorized bearer token; pairing may need to be restored.
- 403: forbidden origin or missing expected request headers.
- 409: call action invalid or rejected.
- 413: invalid body size.
- 431: overly large headers.
- 404: unknown route.

The server uses TLS and sets no-store and other browser headers. Native clients refuse redirects, mismatched certificates and plaintext fallback. Existing saved tokens require one explicit certificate verification after migration. Tokens must not be logged or included in diagnostic screenshots.

## Encrypted media listener, TCP 47322

This is **not HTTP**. After accepting a socket, the host issues an HMAC challenge using the existing paired token fingerprint. All subsequent voice frames use directional AES-256-GCM. See [Voice protocol](VOICE_PROTOCOL.md) for the binary format.

Only one companion voice session is intended at once. The host checks for an active call and the helper, then issues `STREAM:<capture>:<uplink>` over the local shell socket.

The host may send an initial encrypted `PHNLNK:ERROR:...` status instead of READY. A failure before authentication closes the socket without leaking protected state.

## Loopback-only shell helper, TCP 47327

This is a local implementation detail, not an app API. Its first UTF field is a private per-install key. The next UTF field is `PING`, `CAPTURE`, `INJECT`, `STREAM:...` or `STOP`. Its startup response is a UTF string. On success, media after STREAM_READY is unencrypted PCM only because it is transported solely over the host's loopback interface.

Do not expose this port to the LAN, VPN or internet.

## Compatibility policy

The JSON API has no public version negotiation. Version 0.9.3 changes port 47321 from HTTP to HTTPS; both apps must be updated and the host identity verified once, retaining Android saved pairing tokens. The media handshake magic is `PHN1`. Update **both phones** when protocol structures change. When changing packet layout or HMAC labels, introduce explicit protocol negotiation before shipping. Error codes are diagnostic, not public guarantees.

For security review see [Security](SECURITY.md).
