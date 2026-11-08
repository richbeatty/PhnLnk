# Phone Link architecture comparison and separate-network strategy

Reviewed: November 8, 2026. This is a design investigation and technical recommendation, not a claim of implementation.

## User's non-negotiable outcome

Two existing Android devices, on different networks if needed, should behave as though one can make and answer actual calls using the original SIM, AT&T number, and carrier account in the other. The companion must carry genuine two-way speech, not only dial a number. The original phone can stay home and does not need a screen visible for normal calls. No additional hardware gateway, other paid SIM, number porting, root, Shizuku, or forced replacement of the stock Phone app. Optional user-approved one-time Wireless Debugging pairing is allowed, but Android must approve it.

This separates into two hard independent problems:

1. **Audio access at the SIM host**. The host must extract caller downlink PCM and inject companion uplink PCM into an actual carrier voice call. This is firmware/platform dependent.
2. **Authenticated internet reachability and stream quality**. The companion must reach the host from mobile data, another Wi-Fi network, behind NAT/CGNAT, without public unencrypted HTTP ports, and transport audio within tolerable delay.

Solving either part alone does not solve the product.

## Microsoft's Windows Phone Link is a good user experience model, but not an internet audio relay

Official Microsoft documentation says Phone Link's **Calls** feature requires Bluetooth connectivity between the smartphone and the Windows PC. The PC is therefore close enough to act as a Bluetooth hands-free endpoint for the carrier call. A separate Wi-Fi/cloud connection may support other Phone Link features, but that is not evidence that the phone-call audio can be carried when the PC and phone are miles apart.

Sources:

- Microsoft, "Setting up calls in the Phone Link": https://support.microsoft.com/en-us/windows/apps/phonelink/setting-up-calls-in-the-phone-link
- Microsoft, "Make and receive phone calls from your PC": https://support.microsoft.com/en-us/windows/apps/make-and-receive-phone-calls-from-your-pc
- Microsoft, "Troubleshooting calls in the Phone Link": https://support.microsoft.com/en-us/windows/apps/phonelink/troubleshooting-calls-in-the-phone-link

### Why Bluetooth matters

The Bluetooth Hands-Free Profile (HFP) normally has an **Audio Gateway** on the SIM phone and a **Hands-Free client** on the other device. The gateway's system telephony audio router already understands the cellular call. An HFP endpoint can therefore hear and speak without capturing protected downlink PCM from an ordinary Android application.

Windows supports the PC-side hands-free scenario. Two arbitrary stock Android phones running ordinary third-party apps are not equivalent: Android's `BluetoothHeadsetClient` is an @SystemApi / hidden API, and important client operations require `BLUETOOTH_PRIVILEGED`. A normal app cannot assume it may impersonate a hands-free car kit. See Android Open Source Project source:

https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/framework/java/android/bluetooth/BluetoothHeadsetClient.java

The public Android `BluetoothHeadset` API is mainly the smartphone audio-gateway side, not a general client that receives cellular HFP PCM.

### Viability of Phone Link-inspired options

| Idea | Audio path | Fits owner's requirements? | Evidence gap |
| --- | --- | --- | --- |
| Copy Phone Link's Bluetooth calling architecture | Host phone sends SCO/HFP audio to a nearby hands-free endpoint | Not for remote networks by itself | Android companion HFP client is restricted and Bluetooth range is short |
| Make companion itself a Bluetooth headset | Host uses its built-in cellular HFP gateway | Only if a privileged/system headset client can be implemented and devices are close | Need actual Android HFP role/access confirmation |
| Put a Windows/Linux HFP endpoint at the host and send audio over IP | Bluetooth at home, internet to companion | **No**, adds a third gateway device | Hardware violates owner's constraints |
| Privileged shell cellular capture/injection on SIM host | Shell AudioRecord/AudioTrack to encrypted IP media | Potentially matches two-device goal | MP01 audio HAL source and injection have not been verified |
| VoIP provider forwarding the user's calls | SIP/RTP over internet | Can work technically | Changes routing, may add service fee and must preserve outbound identity |
| Carrier-supported multi-device number sharing | Carrier delivers call to second registered endpoint | Potentially reliable with supported endpoints | AT&T product/device eligibility, existing companion support and carrier constraints |

**Conclusion:** Use Phone Link's one-time pairing, dialer, connection indicators, notifications and hands-free UX as inspiration. Treat actual Bluetooth call audio as an alternate platform-specific research branch, not an already available solution to distant networks.

## Internet connectivity options

### Option A: managed private overlay VPN, suggested first engineering milestone

Install a reputable existing overlay such as Tailscale on the **two existing Android devices**. Use the host's stable numeric overlay address (DNS names are not implemented) instead of its transient home Wi-Fi address. Require the VPN on both endpoints. That is extra software, not an additional device.

Advantages:

- Both phones can be on different Wi-Fi networks or cellular data, including behind NAT/CGNAT where the VPN supports relay traversal.
- No public port forwarding or new caller phone number.
- PhnLnk's existing control and voice sockets could use private overlay IPs after compatibility and service-lifecycle checks.
- Avoids implementing new NAT punching, TURN, identity and secure session orchestration prematurely.

Important qualifications:

- The VPN does **not** grant Android call audio access. Host carrier audio remains the first gate.
- PhnLnk does **not** currently provision or manage the VPN, discover overlay endpoints, or verify remote network state. The README must not imply this is turnkey.
- From 0.9.3 pairing and control use pinned HTTPS even inside the VPN. Local device compromise, stolen prior tokens, Android lifecycle and overlay availability still need separate controls.
- The user's ideal one-app setup would require an embedded secure tunnel or integrated provider onboarding later.
- Verify mobile data, CGNAT, Wi-Fi handovers, battery policies, address stability, and that the system VPN is actually connected on both phones.

References:

- Android VPN app architecture: https://developer.android.com/develop/connectivity/vpn
- Tailscale Android app split tunneling: https://tailscale.com/docs/features/client/android-app-split-tunneling

### Option B: first-party secure rendezvous and relayed transport

For a product-level one-app experience, design a small identity and rendezvous service to exchange ephemeral authenticated connection information, and use existing audited media transport such as WebRTC for direct UDP paths plus TURN fallback. A managed relay is network infrastructure, not an extra physical device at the host.

Requirements:

- User-approved account or device-pairing identity, with a migration from the existing pinned-TLS bearer model to independently authenticated endpoint identities.
- End-to-end authenticated confidentiality for **both control and voice**.
- A relay that cannot place calls without an authorized device identity.
- NAT traversal with UDP direct paths when possible and TURN relaying when not.
- Incoming call signaling, push notification/Android foreground service controls, call invitation expiration and rate limits.
- Opus voice encoding, jitter buffer, echo cancellation, latency monitoring, reconnection and call teardown.
- Metadata retention policy and avoidance of transporting call audio to untrusted parties.
- No unencrypted cloud endpoint that can issue dial/answer commands.
- No claim that this remote service solves the host's Android audio HAL access.

This is a larger system than the current TCP PCM proof of concept. Do not replace the established code without migration and a coherent incremental plan.

### Option C: directly expose TCP 47321/47322 with port forwarding

**Rejected.** Versions before 0.9.3 used plaintext HTTP and exposed pairing tokens. Pinned HTTPS now addresses that transport flaw, but does not provide public-service abuse controls, discovery, robust availability or Android lifecycle guarantees. CGNAT also makes direct forwarding unreliable. Keep both listeners private.

## Recommendation and sequence

1. **Prove or disprove a host cellular audio backend** on the owner's exact MP01 firmware using independent analysis, existing comparable code, or a controlled physical test only after a credible implementation is available. Do not keep distributing speculative APKs.
2. In parallel, harden the existing HMAC/AES socket, startup errors, app lifecycle and staged diagnostics using automated tests.
3. Add explicit remote-network support through a **private VPN integration path first**. Require stable endpoint discovery and no public-port exposure. With two existing devices, this is the lowest engineering-risk separate-network MVP.
4. After audio is verified and control traffic is secured, investigate first-party discovery, secure signaling and a WebRTC/Opus/TURN system for effortless one-app operation.
5. Keep Bluetooth HFP as a carefully labeled platform-specific experiment. An Android HFP client may require system privileges unavailable under the user's constraints; it also cannot span the distance alone.
6. If no acceptable Android audio route can work on the MP01, state that clearly and present mutually exclusive trade-offs, such as carrier support, call forwarding or hardware changes. Do not hide the issue with more AES/HTTP code.

## Success conditions for remote calling

A main phone at home on a private Wi-Fi network and a companion on **cellular mobile data** must be able to receive a ringing notification, answer, hear and send understandable human speech, use the keypad where feasible, hang up, reconnect after interruptions and avoid public unauthenticated access. Repeat with the companion on another Wi-Fi network. Both the host's stock Phone app and the existing number must continue functioning, with a safe failure mode.

The encryption/key exchange must cover **all call-control commands and media**, not just audio. A successful status ping or AES-GCM test is not remote calling acceptance.

This is a research plan and acceptance contract, not a claim that the current source has achieved those conditions.

## Concrete private-VPN integration path (0.9.3)

This documents the next controlled integration, not a request for another speculative owner install. Install/sign in to Tailscale on the two existing Android devices in the same authorized tailnet and accept Android's VPN consent. Keep PhnLnk included in the tunnel; app-based exclusions can bypass it. Configure access so only the companion can reach the host's TCP 47321 and 47322. Do not include ADB or the loopback helper port. Do not forward any home router port.

Use the host's stable numeric `100.64.0.0/10` address in PhnLnk's connection settings, retaining its certificate pin and token. New host QRs can prefer an overlay IPv4 address on tun/wg; this is address enumeration, not proof that a VPN is authenticated or connected. MagicDNS and IPv6 are not implemented in PhnLnk. Manually configured WireGuard can also use a permitted private IPv4 address, but requires a reachable peer configuration; bare WireGuard alone is not a NAT rendezvous or DERP substitute.

Tailscale can use direct NAT-traversed connections or encrypted DERP relay fallback. Those hosted services are infrastructure, not a third device at home. Direct and relayed paths have different latency and depend on the service's current terms/availability. No account or subscription was created here. Android normally provides one active VpnService slot: another VPN may conflict. Always-on settings and host battery policy can improve availability, but must be chosen through Android and physically tested. PhnLnk cannot silently enable the VPN or Wireless Debugging.

Before an audio claim, verify pinned HTTPS status/revocation first with host home Wi-Fi and companion Wi-Fi disabled using mobile data, then unrelated Wi-Fi. Verify the phone is reachable through the overlay rather than its home LAN. Record direct/relayed path and handoff behavior where available. Then separately apply the real-speech gates in [Test plan](TEST_PLAN.md). An inaccessible overlay must fail visibly without falling back to public HTTP or automatically redialing.

Primary references: [Tailscale Android setup](https://tailscale.com/docs/install/android), [connection types](https://tailscale.com/docs/reference/connection-types), [DERP](https://tailscale.com/docs/reference/derp-servers), [Android split tunneling](https://tailscale.com/kb/1444/android-app-split-tunneling), [Android VPN architecture](https://developer.android.com/develop/connectivity/vpn).

## Built-in WebRTC design, explicitly not implemented

Keep the audio backend behind a capability/evidence gate; WebRTC does not create modem access. After the MP01 speech path is established, a versioned design should provide:

| Component | Required behavior and trust boundary |
| --- | --- |
| Persistent device identity | Separate device signing keys (for example AndroidKeyStore P-256), public keys authenticated by physical QR pairing. Retain existing pairing until deliberate migration; revoke by device identity |
| Rendezvous/signaling | TLS to the service plus end-to-end encrypted, endpoint-signed invitations/SDP/ICE bound to call ID, nonce, expiry and paired device keys. Authenticate the DTLS fingerprint inside that exchange so a malicious signaling server cannot substitute a peer |
| Media | Maintained native WebRTC, DTLS-SRTP, Opus (typically 20 ms frames), adaptive jitter buffer, packet loss concealment and congestion handling. Evaluate native 48 kHz HAL adaptation separately from network framing |
| NAT traversal | ICE with STUN candidates and authenticated TURN fallback for CGNAT/restrictive Wi-Fi. STUN alone cannot guarantee reachability; offer relay-only mode for peer-IP privacy if needed |
| Relay credentials | Short-lived scoped TURN credentials, quotas and abuse controls. Budget bandwidth/hosting before deployment; keep TURN and signaling logs free of speech, tokens and contact data |
| Audio processing | Validate WebRTC echo cancellation/noise processing with selected Android routes. Avoid unintentionally stacking hardware and software AEC. Voice quality and route changes require measured tests |
| Network handoff | Connectivity callbacks, ICE restart and bounded retry/backoff; establish a new authenticated session on reconnect. Do not silently replay dial/answer or resume microphone capture without Android-authorized user action |
| Control semantics | Authenticated call/session IDs and idempotent operation IDs; avoid duplicate dial/DTMF on retry, expire stale ringing invitations and reconcile active state after recovery |
| Incoming calls | Optional FCM or equivalent push as an opaque wake hint, then fetch authenticated current call state. No numbers, tokens or SDP in push. Polling/Doze latency is not reliable push delivery |
| Android lifecycle | Visible user-approved host service; companion notification opens the calling flow. Android 14 restricts background microphone foreground-service starts, so a push message is not permission to activate the mic. Explicit answering/start remains required |
| End-of-call privacy | Stop mic, effects, playback, ICE/DTLS and helper sessions on hangup, revocation or fatal failure; host stock Phone app remains usable |

The service learns connection timing and routing metadata even when signaling payloads and audio are end-to-end encrypted; TURN can observe IPs and traffic volume. Define retention and deletion policies before hosting. Use sustained network-loss, relay-only, stale invitation, key mismatch, replay and multi-client tests before deployment. No first-party signaling, TURN, FCM credential or account has been provisioned by this work.

References: [WebRTC peer connections and ICE](https://webrtc.org/getting-started/peer-connections), [TURN configuration](https://webrtc.org/getting-started/turn-server), [Android foreground-service launch restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start). The engineering choice is to finish the MP01 audio and current TLS/overlay runtime gates before a large transport rewrite.
