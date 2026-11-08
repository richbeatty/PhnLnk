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

Install a reputable existing overlay such as Tailscale on the **two existing Android devices**. Use the host's stable overlay address or DNS name instead of its transient home Wi-Fi address. Require the VPN on both endpoints. That is extra software, not an additional device.

Advantages:

- Both phones can be on different Wi-Fi networks or cellular data, including behind NAT/CGNAT where the VPN supports relay traversal.
- No public port forwarding or new caller phone number.
- PhnLnk's existing control and voice sockets could use private overlay IPs after compatibility and service-lifecycle checks.
- Avoids implementing new NAT punching, TURN, identity and secure session orchestration prematurely.

Important qualifications:

- The VPN does **not** grant Android call audio access. Host carrier audio remains the first gate.
- PhnLnk does **not** currently provision or manage the VPN, discover overlay endpoints, or verify remote network state. The README must not imply this is turnkey.
- The existing PhnLnk HTTP control channel is plaintext; a secure authenticated VPN can protect it from transport observers but does not fix all local device or token theft concerns.
- The user's ideal one-app setup would require an embedded secure tunnel or integrated provider onboarding later.
- Verify mobile data, CGNAT, Wi-Fi handovers, battery policies, address stability, and that the system VPN is actually connected on both phones.

References:

- Android VPN app architecture: https://developer.android.com/develop/connectivity/vpn
- Tailscale Android app split tunneling: https://tailscale.com/docs/features/client/android-app-split-tunneling

### Option B: first-party secure rendezvous and relayed transport

For a product-level one-app experience, design a small identity and rendezvous service to exchange ephemeral authenticated connection information, and use existing audited media transport such as WebRTC for direct UDP paths plus TURN fallback. A managed relay is network infrastructure, not an extra physical device at the host.

Requirements:

- User-approved account or device-pairing identity, independent from the current unencrypted HTTP bearer.
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

**Rejected.** The existing HTTP control/pairing channel is not TLS encrypted, contains powerful call-control authorization, and is not suitable for the public internet. Even though port 47322 encrypts media, the pairing/control channel remains a security vulnerability. CGNAT also makes direct forwarding unreliable.

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
