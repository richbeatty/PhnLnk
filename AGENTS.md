# PhnLnk agent instructions

You are maintaining a real Android telephony project, not a disposable prototype.

**Read before modifying code:**

1. [Project contract](MAINTAINER.md)
2. [Documentation index](docs/INDEX.md)
3. [Advanced coding-agent handoff](docs/CODER_AGENT_HANDOFF_PROMPT.md)
4. [Phone Link and different-network architecture research](docs/PHONE_LINK_AND_REMOTE_NETWORKS.md)
5. [Known limitations](docs/KNOWN_LIMITATIONS.md)
6. [Test plan](docs/TEST_PLAN.md)
7. [Security review](docs/SECURITY.md)
8. [Release playbook](docs/RELEASE_PLAYBOOK.md)

## Requirements that must survive every iteration

- Original AT&T SIM, number, carrier, MP01 host and second existing Android device only. No new gateway hardware, service-line replacement, root, Shizuku or forced default dialer role.
- The companion must eventually carry actual far-end speech and uplink speech while on another Wi-Fi network or mobile data, not merely ring or control a call on the host.
- The user explicitly permits optional, guided, Android-approved one-time Wireless Debugging pairing to investigate host audio. This does not authorize silently turning debugging on or granting privileges.
- Basic call control must work without debugging. The existing stock Phone app remains default and available for emergency calls.
- Existing paired-device identity must survive ordinary app updates and restarts.
- Do not deliberately wake the main phone screen just to receive calls. Android's own behavior may differ.
- Protect **both** control and media communications before describing the app as safe over public networks. Control 47321 uses pinned HTTPS from 0.9.3, including pairing. Do not reintroduce plaintext fallback or port forwarding.
- The shell helper's readiness and a passing encryption test do not prove carrier audio. The initial VOICE_CALL capture was reported silent on the MP01. The specific MP01's alternative downlink and uplink routes remain unverified.
- The user has asked not to be repeatedly sent experimental APKs to test. Exhaust automated and independent evidence first.
- November 10 update: the owner explicitly requested a new APK to test. A clearly labeled experimental candidate is now authorized after automated gates; retain the permanent signing identity and do not claim MP01 speech is proven.
- Do not make promises of hardware compatibility or release readiness that are not supported by tests.

## Architecture invariants

- Host private call-control HTTPS: TCP 47321, bearer token, verified certificate pin and persistent AndroidKeyStore host identity. Preserve saved tokens through explicit TLS migration.
- Host encrypted media: TCP 47322, HMAC pairing proof and directional AES-256-GCM.
- Shell audio helper: TCP 127.0.0.1:47327 only, separate local secret, must be shell UID 2000.
- Audio frame: 16 kHz mono, signed 16-bit little-endian, 640 bytes for about 20 ms. No text errors after raw PCM streaming starts.
- Media handshake must reject wrong tokens and altered or replayed ciphertext. Connection errors must name their stage, never silently leave the user with a bare EOFException when a useful error can be generated.
- Android self-ADB uses a bounded persistent exec:sh command stream, not the earlier short-lived shell: stream that exhibited a documented libadb close race.
- Keep host lifecycle, voice socket cleanup, companion microphone foreground service, mute/route controls and call state monitoring.
- Experimental capture backends: call extraction REDIRECT, VOICE_DOWNLINK, VOICE_CALL. Uplink REDIRECT or telephony output. Initialization is not proof of audibility.
- Do not assume nearby Bluetooth HFP is available as an Android headset client. Android hides and restricts the headset-client API; it cannot by itself span separate networks.
- Remote connectivity options must include trusted private VPN first and a long-term secure rendezvous/relay with authenticated control, NAT traversal and jitter-aware realtime media.

## Before every commit

Run and inspect:

```bash
python3 tools/maintainer_check.py
bash tools/test_voice_protocol.sh
bash tools/test_control_tls.sh
python3 tools/test_audio_policy.py
bash shellprobe/build.sh
gradle --no-daemon :app:assembleDebug
gradle --no-daemon :app:assembleRelease
```

Android CI validates compilation and protocol mechanics, **not** a real cellular call on the MP01. Keep versionCode increasing, app ID `com.richbeatty.phnlnk` unchanged and permanent release signing identity pinned. Never commit private signing keys, passwords, bearer tokens, ADB credentials, phone numbers, contacts or recordings.

Update `CHANGELOG.md`, `MAINTAINER.md` and affected docs under `docs/` with every material change. Keep an explicit distinction between implemented, built, protocol-tested, real device-tested and not implemented.

A Windows Phone Link comparison or outside open-source project can inspire an architecture but does not constitute proof on this phone. Keep the record of attempted methods and reject ineffective or insecure shortcuts.

The owner values a clean native UI but functional two-way audio and robust remote connectivity take priority over appearance.

Do not present PhnLnk as an emergency-calling solution.
