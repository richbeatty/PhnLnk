# Resumed engineering status — November 10, 2026

Later owner direction on November 10 explicitly requested a new APK. The subsequent [0.9.4 candidate](TEST_BUILD_0.9.4.md) implements the optional native uplink adapter described as future work below. This report preserves the earlier analysis stage. The signed 0.9.3 workflow was attempted and stopped at the missing permanent signing credentials gate; no installable release resulted. The owner is unsure where the original key is stored. Do not substitute a debug or newly generated release identity.

**The requested two-device, different-network cellular calling solution remains unproven on MP01.** Public source establishes candidate audio routes, not that the owner's installed firmware delivers speech. There is no connected MP01 in this environment. It would be unjustified to declare either success or universal impossibility, or to send another speculative APK.

## Baseline and preserved work

GitHub main was rechecked as `198710f542fb410212755bd5475a036321935785` (0.9.3, versionCode 17). The checkout was initially clean. Read AGENTS.md, MAINTAINER.md, the required handoff/security/test/release documents and both prior engineering reports before editing.

Reviewed the 0.9.3 changes and current helper, transport, relay, companion media lifecycle, pairing/control and build workflow. This follow-up changes offline analysis, documentation and TLS test handling only. Pinned HTTPS, explicit token-preserving trust migration, V3 prepared/pumped Looper, pre-READY errors, the post-READY binary guard, bounded authentication, serialized GCM sends, revocation and microphone cleanup remain in place. Package identity, Android version/code, pairing preferences, ADB identity and release signing configuration are unchanged. No release key was accessed. No APK is being distributed.

## Audio-path findings

Both public policy XML files were downloaded again at Minimal revision `1dff2f7fa804af63e8c9c5f5ddaae6af4a44b74a`; their SHA-256 hashes match the original audit exactly. Machine-readable comparisons and decoded Android 14 source hashes are in [native-format review](research/MP01_NATIVE_FORMAT_REVIEW.json). Source links and the broader privilege chain remain in [MP01 feasibility](MP01_AUDIO_FEASIBILITY.md).

| Candidate | Published MT6789 declaration | Engineering implication |
| --- | --- | --- |
| Downlink | Voice Call In (`IN_VOICE_CALL`, alias of TELEPHONY_RX) → `voice tx`, PCM16 mono at 8/16/32/44.1/48 kHz | Both current 16 kHz mono and native 48 kHz mono are declared on the capture mix. Silence cannot be attributed solely to a missing 16 kHz profile |
| Uplink playback mix | `incall_music_uplink`, INCALL_MUSIC, PCM16/32 stereo at 44.1/48 kHz → Telephony Tx | Current helper 16 kHz mono is not an exact mix-profile match. 48 kHz stereo is a justified research candidate, not proven modem injection |
| Uplink device | Telephony Tx declares PCM16/32 **mono** at 44.1/48 kHz | Mix and device formats differ in the published graph. Do not require identical masks across them or assume where conversion occurs |
| Vendor/common variant | Telephony Tx sources are hardware microphones, with no routed software playback mix | Platform XML cannot be assumed installed; the common variant cannot be reported as exposing the same software uplink |
| Project build flag | Z10 ProjectConfig sets MT6789 and `MTK_PHONE_CALL_FIXED_48K_SAMPLERATE = yes` | A build-time modem/audio setting is not proof every Java client must request 48 kHz, or that a particular policy was installed |

The inspector now emits exact declaration comparisons for PCM16 at 16 kHz mono, 48 kHz mono and 48 kHz stereo on routed mix ports. It never combines rate/channel/encoding fields across different profiles. Missing or dynamic profiles remain UNKNOWN unless an explicit incompatible field rules the candidate out. This is deliberately narrower than format compatibility: it does not model mixer conversion, route activation, grants, attribution, SELinux, HAL operation or intelligibility. Includes remain unresolved and explicitly listed. The tool does not fetch them.

Reproduce using the linked pinned policy files:

```bash
python3 tools/inspect_audio_policy.py /path/to/mt6789/audio_policy_configuration.xml
python3 tools/inspect_audio_policy.py /path/to/vendor-common/audio_policy_configuration.xml
python3 tools/test_audio_policy.py
```

## Android 14 route selection and restrictions

Reviewed pinned `android-14.0.0_r1`, rather than assuming current Android main matches this phone:

1. [AudioPolicyManager::getOutputForAttrInt](https://android.googlesource.com/platform/frameworks/av/+/android-14.0.0_r1/services/audiopolicy/managerdefault/AudioPolicyManager.cpp#1263) sets INCALL_MUSIC for an explicitly selected Telephony Tx port when the stream is MUSIC or usage is VOICE_COMMUNICATION, the encoding is linear PCM, and call audio is accessible. `isCallAudioAccessible()` accepts IN_CALL, CALL_SCREEN and CALL_REDIRECT. A normal-mode playback test is not the equivalent of a carrier-call route test.
2. [AudioPolicyService output enforcement](https://android.googlesource.com/platform/frameworks/av/+/android-14.0.0_r1/services/audiopolicy/service/AudioPolicyInterfaceImpl.cpp#394) checks MODIFY_PHONE_STATE for Telephony Tx and additionally CALL_AUDIO_INTERCEPTION for call-redirection output. Reflection does not supply these grants. Verify actual shell grants and truthful attribution on the installed firmware.
3. [AudioManager extraction/injection](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/media/java/android/media/AudioManager.java#8824) are hidden system/test APIs. Their builders and mode listeners have their own attribution/lifecycle behavior; wrapping the manager's context is not proof of final track attribution. Successful eligibility checks or API construction still do not prove speech.
4. [getOutputForDevices](https://android.googlesource.com/platform/frameworks/av/+/android-14.0.0_r1/services/audiopolicy/managerdefault/AudioPolicyManager.cpp#1583) considers direct outputs and non-direct mixer selection. Therefore an absent exact 16 kHz uplink declaration does not prove Android cannot convert it. Conversely a matching 48 kHz profile does not prove the MediaTek HAL connects it to the modem.

The current TELEPHONY backend builds a USAGE_MEDIA track, then requests TYPE_TELEPHONY before play. Its actual selected output, flags and routed device must be observed; acceptance of setPreferredDevice is not proof of a modem route. Do not force MODE_IN_CALL from PhnLnk, mute the host output, change the stock dialer, or silently make a native backend the default on these static findings.

## Concrete software repair and tests

The first JDK 17 rerun passed all 22 production voice transport cases and 9 production relay cases. The TLS suite reproduced a server-side Broken pipe during the expected changed-certificate rejection: TLS 1.3 can be writing Finished when the client rejects the certificate and closes TCP. The negative-test server caught SSLException only, so a correct security outcome could fail CI.

The harness now permits SocketException only in certificate-rejection server cases. The client still must throw SSLHandshakeException. Any application byte received in the changed-identity case still fails an AssertionError. Positive traffic and discovery do not ignore socket errors. Production TLS trust code was not weakened or changed. All five TLS cases then passed with default negotiation, forced TLS 1.2 and forced TLS 1.3. Seven policy cases pass, including the preserved real-policy summaries and three new regression scenarios. These are 43 distinct automated cases, plus repeat TLS protocol-version runs.

Final local verification used JDK 17.0.21, Gradle 8.9, Android platform 35 and Build Tools 35.0.0 for helper DEX generation; AGP also required Build Tools 34.0.0. All results remain separate from physical-device acceptance.

| Check | November 10 result |
| --- | --- |
| Maintainer invariants and diff whitespace | PASS |
| Production voice transport | 22/22 PASS |
| Production relay | 9/9 PASS |
| Production TLS primitives | 5/5 PASS with TLS 1.2; 5/5 PASS with TLS 1.3; negotiated version asserted |
| Offline policy inspector | 7/7 PASS |
| Shell helper DEX | PASS; existing deprecated prepareMainLooper warning, no source change |
| Android debug and unsigned release | PASS; both assembled, 79 tasks executed |
| APK ZIP integrity and bundled helper | PASS; both contain the exact freshly built helper asset, which contains classes.dex |
| Android device inventory | Empty; no physical runtime, modem, speech, VPN or signed-install tests |

Initial tooling was absent. Installed official user-owned JDK/Gradle/SDK tools outside the repository. SDK manager could not fetch manifests through its default Java networking, so official archives were fetched using the inherited HTTPS proxy and checked against the repository manifest checksums. Gradle required explicit Java proxy/CA configuration and Build Tools 34.0.0 as well as the helper's 35.0.0. These environment setup issues were resolved without changing project build or signing configuration. Deprecation and optional native-symbol stripping warnings did not fail compilation. Debug and unsigned release outputs remain local build artifacts; they are not proposed owner updates.

## Prioritized blockers and decisions

| Priority | Missing evidence | Decision |
| --- | --- | --- |
| P0 | Installed MP01 build, selected vendor policy/includes, shell grants, real input/output route and HAL stream format | Obtain from an independent developer/OEM MP01 or a matching authorized firmware export. Public source alone cannot close this gate |
| P0 | Far-end-only downlink, companion-only uplink and simultaneous intelligible speech | Keep two-way calling unverified. Use acoustic isolation and negative mute controls from TEST_PLAN; RMS and accepted writes cannot close it |
| P1 | AndroidKeyStore migration, signed-update identity persistence, Android 14 foreground microphone and cleanup behavior | Preserve implementation and existing identity; keep on-device gates open |
| P1 | Host home Wi-Fi / companion mobile data and unrelated Wi-Fi, screen off and roaming | Validate private connectivity and speech separately after the audio gate |
| P2 | Integrated rendezvous/media, latency, push reliability and recovery | Defer transport rewrite until the host produces and accepts real speech |

No new physical-device evidence was available to explain the owner's earlier silent VOICE_CALL result or particular EOF. The 0.9.3 EOF fixes remain justified software repairs, not a retrospective diagnosis of that incident.

## Next implementation decision

If a matching installed policy and observed route confirm the platform incall mix is usable, implement a **separately selectable**, policy-matched adapter: retain 16 kHz mono / 640-byte encrypted wire frames; convert uplink to the confirmed native track rate/mask; convert native capture back only if needed. At 48 kHz stereo PCM16, one 20 ms block is 960 frames / 3,840 bytes; it must not be sent as a 640-byte wire frame. Require automated duration, chunk-boundary, clipping, channel and filter/aliasing tests before Android integration. No adapter has been added here because the live format/route is still unknown.

If grants/attribution fail, address that exact authorized-shell failure first. If the selected policy/HAL excludes software uplink or returns only zeros on every authorized route, an APK cannot add the missing vendor path. OEM-supported firmware/system integration on the same MP01 is the smallest currently identified conditional change that preserves both devices, SIM, carrier, number and stock Phone app. OEM feasibility itself remains unknown.

After speech is established, use an independently provisioned authenticated private overlay on the two phones, keep certificate pin/token when changing to the stable private address, and allow companion → host TCP 47321/47322 only. NAT/CGNAT relay support is needed; plain WireGuard configs alone do not guarantee reachability. Do not expose ADB/helper ports or forward router ports. The [existing network design](PHONE_LINK_AND_REMOTE_NETWORKS.md) already covers Tailscale direct/DERP, Android VPN-slot restrictions and later authenticated WebRTC/Opus/ICE/TURN. No tunnel, account, cloud relay or subscription was provisioned, and no cross-network call is claimed.
