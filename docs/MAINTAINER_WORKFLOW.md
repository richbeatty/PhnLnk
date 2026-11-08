# Maintainer workflow

The maintainer's primary job is to preserve the user's requirements while replacing guesses with verified behavior.

## Product contract

- Keep the existing AT&T SIM, number and original Phone app.
- Only an existing main Android phone and existing companion are permitted. No additional headset, computer gateway, paid second SIM, number porting, root or Shizuku.
- The user explicitly allowed a **guided optional** Android Wireless Debugging pairing and automatic reconnect while debugging remains enabled.
- The app may not enable Developer options or approve its own ADB identity silently.
- Do not require debugging for normal dial, answer or hangup controls.
- Default Phone app role is **not authorized by default**. User was open to reconsideration only if there is a proven functional benefit, not for cosmetic improvements.
- No product claims about remote cellular speech unless the MP01 has passed a real two-way call.
- No public unencrypted server, silent permission escalation, secret leakage, or destruction of saved pairings during normal updates.
- Keep the host screen from intentionally waking for remote calls, while recognizing Android itself may wake it.

## Repository map

- `app/src/main/java/com/richbeatty/phnlnk`: Android host and companion classes.
- `shellprobe/src`: Small Android shell UID audio helper. It must not be network accessible.
- `tools`: Product invariants and pure JVM protocol tests.
- `docs`: Architecture, security, API, test protocol, troubleshooting and decisions.
- `MAINTAINER.md`: Living user-centered requirements.
- `CHANGELOG.md`: Version history.
- `.github/workflows/android.yml`: Build and signing contract.

Start each substantive change by reading the affected code and its documentation. Do not replace an established approach with a new prototype simply because it looks simpler.

## Branch and commit discipline

1. State the intended fix and where it belongs: control, helper, encryption, media, UI or Android lifecycle.
2. Record whether the failure has a confirmed source-code cause, a device-specific hypothesis, or only a symptom.
3. Write a reproducer or regression assertion before changing relevant protocol logic where practical.
4. Implement the smallest correct fix, with bounded timeouts and explicit states.
5. Run JVM protocol checks and maintainer invariants; build both shell helper and Android APK.
6. Review security invariants and whether the UI will accurately report the result.
7. Update `CHANGELOG.md`, affected docs, versionCode/versionName and the maintainer contract.
8. Commit the complete change to GitHub and check CI results.
9. Do not distribute a signed APK without verifying the same permanent release certificate.
10. Never claim the change is verified on hardware without a real device test.

## Key architectural invariants

### Pairing and authorizations

- Host stores only paired token hashes. Companion stores its own token in private storage.
- Media HMAC proof binds the voice session to an authorized pairing.
- Revoking pairings terminates current media.
- Shell helper uses a separate per-install secret and loopback socket.
- Do not turn voice HTTP into raw unencrypted media. The voice TCP listener must retain authenticated encryption.
- The HTTP control and initial pairing channel are **still plaintext** and only appropriate on trusted private networks.

### Voice wire format

- Directional AES-256-GCM keys.
- Strict monotonically increasing sequence per direction.
- A fixed 640-byte PCM frame and 16-byte GCM tag.
- No injected UTF control messages after STREAM_READY.
- A failed handshake or truncated stream must report its stage without disclosing secrets.
- Do not reuse an old media nonce with a new plaintext frame under the same key.

### Shell and media lifecycle

- Keep shell UID 2000 check and local 127.0.0.1 bind.
- The host cannot assume shell readiness means cell audio works.
- Avoid the short-lived libadb `shell:` streams responsible for the earlier `Stream closed` regression.
- Keep bounded commands and local ADB reconnect behavior.
- Do not let a helper exception close before sending startup diagnostics when a safe response is possible.
- Do not write startup errors into the binary PCM stream after READY.
- Leave enough initialization time for Android audio backends and the companion microphone.
- When a stream closes, release sockets, AudioRecord, AudioTrack, AEC/NS effects and audio routing.
- Always show critical errors on the companion even if the cellular call is still active.

### UI

- Build reusable views without attaching one Android View to two parents.
- Keep error text readable and selective controls enabled based on actual state.
- Avoid presenting a shell PING or protocol-ready state as successful two-way calling.
- Do not claim the app includes a public internet relay.
- Preserve the user's simple style and avoid exaggerated confirmations.

## Testing rules

The Java protocol harness must be run for changes to handshake framing, AES, error mapping, startup messages or stream timeouts. Static code invariants are supporting checks, not runtime tests. Android CI must build the embedded shell DEX and a debug and release candidate.

Device-specific media routes require real MP01 observations before release promotion. Unverified cases stay in [Known limitations](KNOWN_LIMITATIONS.md).

## Documentation edit rules

- Add a `CHANGELOG.md` entry for every app version.
- Update [Architecture](ARCHITECTURE.md) for service, process or port changes.
- Update [Voice protocol](VOICE_PROTOCOL.md) for handshake, framing, nonce, sequence or error changes.
- Update [Security](SECURITY.md) for authentication, data retention, permissions or exposure changes.
- Update [Troubleshooting](TROUBLESHOOTING.md) for newly diagnosed user-facing failures.
- Update [Test plan](TEST_PLAN.md) when a test is added or when a hardware gate is actually demonstrated.
- Update [User setup](SETUP_GUIDE.md) when user actions change.
- Add an ADR to [Design decisions](DECISIONS.md) for irreversible architecture choices.

These docs are part of the product, not optional afterthoughts.

## Human release authority

Building from GitHub is not permission to push a public signed release. Never move the owner's source-of-truth branch to a destructive rewrite without permission. Publishing a signed release requires correct protected signing credentials and a reviewed claim about what the release actually does.

When uncertain about cellular audio, show the exact uncertainty. The user has asked to stop repeated speculative testing.
