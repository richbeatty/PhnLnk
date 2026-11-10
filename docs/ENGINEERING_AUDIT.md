# Engineering audit and delivery status, November 8, 2026

Follow-up from current main `198710f`: [November 10 engineering status](ENGINEERING_STATUS_2026-11-10.md). The original 0.9.3 record below is retained as history. The follow-up adds offline native-format analysis and fixes a reproduced TLS negative-test race, with no Android runtime changes or claimed MP01 speech result.

Source baseline: `01822cc` (0.9.2). Development changes: 0.9.3, versionCode 17. Package remains `com.richbeatty.phnlnk`. No permanent signing key was accessed or replaced. No GitHub release or installable update was published for this investigation.

## Prioritized findings

| Priority | Finding | Disposition / evidence |
| --- | --- | --- |
| P0 | MP01 downlink and uplink speech unverified | Open. Initial VOICE_CALL was reportedly silent; no MP01 or phone connection available here. Public manufacturer policy and external reports analyzed in [MP01 feasibility](MP01_AUDIO_FEASIBILITY.md) |
| P0 | Plaintext initial pairing and bearer control | Replaced with pinned HTTPS on 47321. Saved tokens retained; one explicit certificate verification required. No plaintext fallback. AndroidKeyStore runtime remains device unverified |
| P1 | Helper creates ActivityThread on an unprepared socket thread | Repaired with prepared/pumped framework Looper and cached attributed context. Android 14 source confirms Handler construction requirement. Helper V3 replaces older process using existing ADB identity |
| P1 | Relay closes socket before sending startup ERROR | Repaired by moving close after error delivery. Production relay regression tests cover actual helper EOF and connection exception |
| P1 | Concurrent media send sequence allocated outside lock | Sequence allocation, encryption and write serialized together. Concurrent sender and simultaneous duplex tests added |
| P1 | Unauthenticated sockets reserve sole voice session | Authentication workers bounded; ownership acquired after authentication, with encrypted busy refusal. Nine-second absolute handshake deadline |
| P1 | Revocation/startup and shutdown races | Pending and active media sockets tracked and closed. Listener shutdown waits for accept termination; test covers rapid restart and revocation |
| P1 | Stopped companion can finish connecting; audio reads can remain blocked | Publish socket before connect, guard startup cancellation, avoid overlapping session scheduling, stop mic/playback on shutdown. Android runtime verification still required |
| P2 | Native formats differ from public MT6789 policy | Public source specifies fixed 48 kHz calls and incall playback profiles at 44.1/48 kHz stereo. Current wire/helper requests 16 kHz mono. Installed profile and HAL conversion must be confirmed before implementing another backend |
| P2 | HTTP worker queue and helper thread creation unbounded | Bounded request queue/workers and helper worker count. Control request socket deadline added |
| P2 | API 30 companion uses API 31 routing methods | Guarded communication-device methods; speakerphone fallback on Android 11. Device behavior unverified |
| P2 | Remote addressing and discovery incomplete | Numeric RFC1918 and 100.64/10 accepted, HTTPS identity retained on address change. Host can prefer an overlay address on tun/wg. No VPN provisioning, DNS discovery or network-state proof |
| P2 | PCM/TCP latency and Android lifecycle not production proven | Open: no Opus, adaptive jitter buffer, handoff recovery or push. Do not deploy a relay before solving audio |
| P2 | DTMF with retained stock dialer | Telecom DTMF generally unavailable without authorized Call objects; in-band fallback remains unverified on cellular IVRs |

## Verification record

Run before commit:

```bash
python3 tools/maintainer_check.py
python3 tools/test_audio_policy.py
bash tools/test_voice_protocol.sh
bash tools/test_control_tls.sh
bash shellprobe/build.sh
gradle --no-daemon :app:assembleDebug
gradle --no-daemon :app:assembleRelease
```

The JVM suite exercises **production** VoiceSecureTransport (22 cases), VoiceRelayServer (9 cases), and ControlTls (5 cases). The policy inspector has four unit tests. Media cases include incorrect pairing, forged host proof, fragmented full duplex, concurrent senders, truncated frames, invalid lengths, timeout, replay and tampering. Relay tests use simulated Android call state and helper PCM; they verify encrypted startup errors, ownership, duplex forwarding and revocation, not a real cellular call.

The shell helper and Android debug/release variants compile with JDK 17, Gradle 8.9, Android API 35 and Build Tools 35.0.0. The helper DEX is checked inside both APKs. Release compilation is **unsigned**; no permanent-signature continuity or installation check is claimed. CI runs the same gates on pushes to main. A source-only push does not invoke the manually dispatched publishing path.

An ADB inventory in this environment returned no devices. AndroidKeyStore, framework bootstrap on MP01, speech direction, foreground-service behavior, screen lock, actual VPN routing, physical notifications and update persistence have not been executed here. No APK was sent to the owner and no speculative installation was requested.

## Cross-network recommendation

Use an independently provisioned Tailscale network on the **two existing devices** for the next controlled remote integration, with host home Wi-Fi and companion mobile data. HTTPS still pins the host through the tunnel, and media still authenticates the saved pairing. Tailscale's NAT traversal/DERP avoids home public port forwarding. Allow only the companion to reach host ports 47321/47322; do not advertise ADB or 47327. WireGuard alone needs a reachable peer or separately configured rendezvous/relay; do not promise two CGNAT phones can connect from bare configs.

Neither an overlay nor TLS grants modem audio. This recommendation is an architecture and documented setup path, not a successful MP01/VPN call test. See [remote-network architecture](PHONE_LINK_AND_REMOTE_NETWORKS.md) for WebRTC signaling, Opus, ICE/STUN/TURN, device identity and background notification design.

## Remaining release gates

1. Independently establish the exact MP01 installed policy, grants, attribution and native audio route. Compare public manufacturer source; do not assume it matches the owner's firmware.
2. Prove understandable far-end-only speech at the companion and companion-only speech at the carrier peer, including simultaneous duplex and negative mute controls.
3. Prove the same during incoming/outgoing calls over mobile data and unrelated Wi-Fi, then screen-off, network handoff and reconnection.
4. Verify AndroidKeyStore identity and saved pairing survive a properly permanently signed update; verify certificate mismatch fails closed on Android itself.
5. Close audio/lifecycle/security acceptance before a release; no repeated speculative APKs.

If vendor firmware excludes every authorized audio path, the smallest preserving change is OEM-supported firmware/system integration on the existing host. No extra device, hosted voice account, carrier change, root, Shizuku or default dialer was introduced.
