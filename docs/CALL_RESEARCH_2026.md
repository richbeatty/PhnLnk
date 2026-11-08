# PhnLnk research: existing mobile number across devices
Research updated: 2026-11-07

This investigation covers Beeper/Google Messages, cellular call forwarding, cloud VoIP, carrier multi-device calling, Android audio restrictions, and working/open-source cellular-to-SIP gateways.

## Permanent conditions

The user currently has an AT&T SIM/number on an existing stock Android host phone and wants calls, including real two-way audio, from an existing secondary Android device and eventually PC while the host remains home. The user rejects rooting, debugging, a Shizuku installation, default-Phone-app requirements, additional physical hardware, carrier changes, extra cellular plans, and recurring login. Use the stable original number; clean native dialer/contacts, incoming alerts, IVR touch tones, same signed app updates, and pairing that persists until revoked.

**Finding:** No verified standard Android application path can capture/inject the stock phone's **cellular modem audio** while satisfying all restrictions. But a provider-based routing model can yield the same user-facing caller ID and incoming calling functionality by **not using the host's call audio path**. That model has an additional paid VoIP service and forwarding requirement. Do not represent it as the host's original AT&T SIM carrying outbound audio.

## Why an ordinary app cannot capture cellular calls

Android developer documentation: a third-party app with ordinary `RECORD_AUDIO` may record its own microphone, but the cellular in-call uplink/downlink require the restricted `CAPTURE_AUDIO_OUTPUT` privilege for a privileged/preinstalled app. `TelecomManager.placeCall` and `ANSWER_PHONE_CALLS` are call controls, not access to cellular PCM. Holding default `ROLE_DIALER` gives an InCallService UI/Call control but not raw cellular voice. `MediaProjection`, screen mirroring, `AudioPlaybackCapture` and `AudioManager.setCommunicationDevice` cannot override that privilege.

- https://developer.android.com/media/platform/sharing-audio-input
- https://developer.android.com/develop/connectivity/telecom/dialer-app
- https://source.android.com/docs/core/permissions/app-streaming (app-streaming privileges are reserved for preinstalled OEM priv-apps)
- https://support.google.com/googleplay/games-on-pc-developer/thread/468919175/official-api-for-cellular-call-recording-in-a-default-dialer

## How real third-party products route calls

### 1. Carrier-supported cloud multi-device lines

- **AT&T NumberSync** enables original-number calls on supported devices without the primary phone, because AT&T's network provisions a second registered endpoint. Current May 2026 Android instructions describe watches, **not arbitrary Android phones/tablets**. Compatible Apple devices are listed separately; some historical Android tablets were marketed, but do not infer support for the user's host + companion combination.
  - https://www.att.com/support/article-modal/wireless/KM1209875/
  - https://www.att.com/support/article-modal/wireless/KM1150917/
- **T-Mobile DIGITS** carries the carrier-provisioned number on Android/iOS/tablet/PC. It is what the user wants technically, but requires eligible T-Mobile service, switching away from AT&T or adding a qualifying T-Mobile line; cannot be accessed by a third-party app for an AT&T consumer line.
  - https://www.t-mobile.com/business/solutions/digits
- **Google Fi Web Calls** supports browser calls, text and voicemail with the Fi number, sometimes with the host phone switched off. It requires Google Fi service, not an AT&T line.
  - https://support.google.com/fi/answer/16710124
- **Samsung Call & text on other devices** demonstrates two-device operation on matched Samsung devices. Samsung's 2026 instructions exclude AT&T/Verizon phones and say newer calling versions require both devices on the same Wi-Fi. This is OEM/account-based, not a general Android SDK.
  - https://www.samsung.com/us/support/answer/ANS10001614/

### 2. Cloud-VoIP forwarding + verified original caller ID (most viable without privileged Android)

**Inbound**:
Caller dials original AT&T number -> AT&T unconditional call forwarding -> VoIP-provider DID (back-end routing number, not the identity shown to callers) -> encrypted provider media -> PhnLnk Android WebRTC app on second device (and/or PC browser), with FCM push notification. The host phone need not be online during calls after forwarding is enabled.

**Outbound**:
PhnLnk remote Android WebRTC client -> provider PSTN trunk -> called person's telephone, with **user-owned AT&T number verified as outbound caller ID**. Twilio, Telnyx and VoIP.ms all document external-number verification. The provider, not the host phone's modem, actually originates the outgoing voice leg.

**Text**:
AT&T number stays with AT&T. Its SMS/RCS remains on original phone, available through Google Messages pairing and Beeper if Google Messages is configured on the host. Voice forwarding generally does not forward SMS/RCS.

Proof:
- AT&T call forwarding: https://www.att.com/device-support/article/wireless/KM1323255/
- Twilio verified external numbers + forwarding: https://help.twilio.com/articles/223179848-Using-a-non-Twilio-number-as-the-caller-ID-for-outgoing-calls
- Telnyx verified external numbers: https://support.telnyx.com/en/articles/6790265-verified-numbers-faq
- VoIP.ms verified caller ID: https://wiki.voip.ms/article/Caller_ID

**Tradeoffs and sharp edges**:
- An additional VoIP account and normally a billed DID (routing number), often monthly and usage-based. This is **not another AT&T SIM/cellular plan** but it is another paid service and a second back-end number.
- AT&T call forwarding may add costs or count forwarded calls against plan allowances. Confirm specific account rules.
- Unconditional forwarding usually means the original host's native SIM dialer does NOT receive inbound calls. To ring both Android devices, register both as clients of the VoIP service or set up its simultaneous ringing. Keep normal outbound AT&T dialing separately if wanted.
- Voicemail, call waiting, missed calls, duplicate ringing and call forwarding failures must be deliberately handled; disable/change forwarding to return to ordinary native incoming calling.
- Outgoing verified caller ID shows the original number in many circumstances, but downstream STIR/SHAKEN/CNAM/spam filtering may differ from calls really originating at AT&T. Caller ID display should be tested.
- Emergency calling must use the host's normal cellular phone, or a separately configured compliant VoIP E911 solution with accurate location. Do not promise portable emergency service from the companion.
- If the user refuses **any additional voice service**, this path is also disqualified.

Approximate published VoIP.ms starting prices (as of November 2026): local DID ~$1.10/month, outbound ~$0.01/min, inbound ~$0.009/min, plus any other fees/taxes and AT&T forwarding charges. Prices vary; verify before purchase.
- https://main.voip.ms/pricing/

**Technical build path**:
Use a provider-supported Android WebRTC/Voice SDK with WebRTC microphone/speaker, authenticated account tokens, provider push/FCM, call lifecycle, mute, hold, RFC 4733 DTMF, contact search, and call logging. The provider handles PSTN routing, NAT traversal and encrypted voice. A browser SDK handles PC. The app does NOT need a default Phone role or debugging to carry its **own VoIP audio**.

- Telnyx Android SDK: https://github.com/team-telnyx/telnyx-webrtc-android (MIT; SIP credentials, WebRTC, incoming/outgoing and FCM)
- Twilio Android Voice SDK: https://www.twilio.com/docs/voice/sdks/android
- Android self-managed ConnectionService: https://developer.android.com/reference/android/telecom/package-summary

This is a *design based on documented provider capabilities*, not an implemented or tested PhnLnk voice release. Do not enable any provider integration without user's explicit approval, since it changes billing and call routing.

### 3. Nearby Bluetooth hands-free relays

Windows Phone Link actually delivers call audio to PC via Bluetooth HFP, requiring the computer and phone to be near each other. A Bluetooth-to-SIP Linux/ESP32 gateway can relay that audio over the Internet, but the user rejects additional hardware. A second stock Android phone cannot generally impersonate a Bluetooth HFP headset using ordinary public SDK APIs; and normal HFP itself remains short-range.
- https://support.microsoft.com/en-us/windows/apps/make-and-receive-phone-calls-from-your-pc
- https://github.com/k-chatz/bluetooth-gsm-sip-gateway

### 4. Remote control, not remote voice

AirDroid Personal can initiate/end a call through a browser but says the computer cannot carry call audio due to technical restrictions. This is the same limitation as PhnLnk's existing call control.
- https://help.airdroid.com/hc/en-us/articles/360007483153-Can-I-Make-a-Phone-Call-with-Computer-s-Mic-and-Speakers

### 5. Google Voice

Google Voice is an internet-calling service with a Google Voice number. Linking the AT&T number allows calls placed to the **Google Voice number** to ring that AT&T line, not a carrier-issued way to clone the AT&T number. Caller ID of Google Voice proxy calls is the Google Voice number. Forwarding the AT&T number to the Google Voice number could deliver inbound audio remotely, but outgoing Google Voice calls would normally show a DIFFERENT number, violating the single-number goal.
- https://support.google.com/voice/answer/165221
- https://support.google.com/voice/answer/3379129

## What Beeper actually does

Beeper is an aggregator for multiple chat networks. It **does not implement audio/video calling**, even in 2026. It explicitly directs users to the network's native calling app.
- https://help.beeper.com/en_US/troubleshooting/can-i-make-calls-or-video-chat-in-beeper

Beeper's SMS/RCS integration is based on Google Messages, which must be the Android host's **default SMS app**. Beeper itself is NOT the default SMS app. Device pairing / account linkage give it access to the existing chat network's messaging interfaces, and the SIM phone must remain reachable to synchronize messages. Google Messages for web offers paired computer/tablet messages, not PSTN audio. Beeper is not extracting secret raw cellular call audio.
- https://help.beeper.com/en_US/chat-networks/google-messages-getting-started-guide
- https://support.google.com/messages/answer/7611075
- https://github.com/mautrix/gmessages
- https://github.com/beeper

**Useful integration discovery**:
Beeper offers experimental **Android Content Provider APIs**, including read and send permissions, chats, messages, contacts, and a Google Messages protocol identifier `gmessages`. A future PhnLnk companion could integrate messages locally if Beeper Android is installed and the user explicitly grants these permissions. This is a real documented integration, not a need to reverse-engineer RCS.
- https://developers.beeper.com/android/content-providers/

Beeper also offers a **Desktop API**, but it only works while Beeper Desktop is running and is local to that machine by default. That would violate a no-third-device setup if we assumed a computer would stay online. It remains an option for users who already have desktop.
- https://developers.beeper.com/desktop-api/

Beeper's own Google Messages connection can be unpaired by account changes, phone migration, Google security controls, or inactivity. Never promise literally eternal pairing.
- https://support.google.com/messages/answer/7611075
- https://beeper.helpjuice.com/en_US/troubleshooting/how-to-fix-google-messages-disconnections-in-beeper

## GitHub cellular audio projects inspected

| Repo | Real cellular audio? | How | Why it misses requirements |
| --- | --- | --- | --- |
| [sparkcyf/android-voice-sip-agent](https://github.com/sparkcyf/android-voice-sip-agent) | Bidirectional tested on several Android models; LAN and secure remote SIP paths demonstrated | Android shell via Shizuku, hidden audio APIs, SIP media; newer Android versions can retain normal default dialer using companion override | Shizuku/shell privilege; hardware-dependent, no MP01 validation |
| [Hinduc0der/voice-sip-agent-selfhost](https://github.com/Hinduc0der/voice-sip-agent-selfhost) | Incoming two-way tested on Nubia NX769J; outgoing from remote client NOT implemented | Same shell route with embedded local SIP registrar | Shizuku, background/reboot caveats, no proven Internet deployment |
| [kai-wu-cortex/AirSIM](https://github.com/kai-wu-cortex/AirSIM) | Cross-device design Android -> iPhone/Watch; substantial code | Android standalone call bridge, Shizuku telephony PCM, normal/default dialer role, Cloudflare relay/Apple Push | Needs Shizuku and default Phone role; client not Android; noncommercial license; testing limits |
| [jemcik/JemRec](https://github.com/jemcik/JemRec) | Both sides recording + greeting injection on selected phones | Self-paired loopback ADB shell service, no separate Shizuku | Developer options and Wireless Debugging, device-dependent; not a live remote gateway |
| [Ph1nt0m-oss/Callvault](https://github.com/Ph1nt0m-oss/Callvault) | Call recordings on select phones | Embedded ADB over Wireless Debugging, sometimes scrcpy server | Debugging required; recording not live remote calls |
| [kitsumed/ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder) | Call recording | Shell privilege via Shizuku | Shizuku required; no bidirectional remote voice |
| [pulpoff/gsm2sip](https://github.com/pulpoff/gsm2sip) | GSM/SIP bidirectional on certain modems | Root and OEM-specific audio DSP paths | Root, tested hardware-specific; not generic |
| [s-xander/Android-sip-gateway](https://github.com/s-xander/Android-sip-gateway) | Yes on specific Qualcomm ROM | Magisk and Qualcomm mixer | Root, custom ROM / hardware |
| [neyro-droid/P2P-PHONE-Call-Gateway](https://github.com/neyro-droid/P2P-PHONE-Call-Gateway) | Root path digital; unrooted path **acoustic speakerphone/microphone** | WebSocket audio and physical microphone | Background noise, echo, privacy, double audio routes; not reliable or hands-free remote digital bridge |
| [team-telnyx/telnyx-webrtc-android](https://github.com/team-telnyx/telnyx-webrtc-android) | Yes, real two-way **VoIP** audio (not modem capture) | Official Telnyx Android WebRTC SDK + provider backend | Additional voice provider and routing needed to use AT&T caller identity |

Do not copy AirSIM's PolyForm Noncommercial source into a commercial PhnLnk product. JemRec's Apache-2.0 and Voice SIP Agent's GPL are different, and any adaptation needs license review.

## Recommendation ranked by current fixed requirements

1. **Strictly keep every restriction including NO additional paid voice provider:** No verified full voice solution exists for the combination of stock AT&T Android number + two remote devices + no privileges. Continue call-control as a clearly limited prototype. Do not ship fake media relays.
2. **If one small pay-as-you-go, cloud-hosted VoIP service is acceptable (no added physical device, no number porting, no carrier switch):** PhnLnk can become a genuine voice app using forwarding to provider DID and verified original outbound caller ID. This is the most supportable product architecture with the fewest OS hacks. It still changes incoming network routing and uses provider billing.
3. **If changing carrier becomes acceptable:** T-Mobile DIGITS and Google Fi Web Calls are production examples with purpose-built account support for remote calls, but they violate the keep-AT&T rule.
4. **If privileged setup becomes acceptable:** Adapt Voice SIP Agent + JemRec-like embedded self-ADB after real MP01 audio proof, with firmware/security/reboot caveats. This violates the user's explicitly stated no-debugging requirement and must never be silently reintroduced.
5. **If extra hardware becomes acceptable:** Bluetooth HFP gateway / Asterisk, already rejected.

## Next technical decisions

- Do not open an implementation issue for a paid provider unless the user explicitly approves a separate voice-service bill and AT&T network call forwarding.
- If approved, prototype direct provider WebRTC on the secondary device, not a fake phone-audio stream. Verify outgoing CLI presentation and real incoming forwarding with a test DID.
- Keep Google Messages/Beeper messaging optional and separate from voice. Explore Beeper Android Content Provider with explicit user permission.
- Validate audio in both directions, outgoing/incoming, DTMF, FCM wake, foreground/background, and signing/certificate continuity before any release called production-ready.

No provider credentials were created and no AT&T forwarding changes were made during this research.


## Additional search findings, 2026-11-07: network-level audio instead of recording

### Candidate: cellular carrier conference as a software-only voice bridge

This is a new engineering hypothesis, **not a verified working PhnLnk solution**:

- AT&T's network supports conventional three-way calling on many phones. A conference call's audio is mixed by the network, not captured by a third-party Android application. See https://www.att.com/device-support/article/wireless/KM1275499/.
- The second Android device could receive one leg over a free internet number, such as Google Voice set to **Prefer Wi-Fi and mobile data**. The first leg could be an actual AT&T cellular call to the intended recipient. The recipient would see the AT&T number if the host originates its leg.
- Newer Android offers `TelecomManager.startConference(List<Uri>, Bundle)` with ordinary `CALL_PHONE` permission, **but it requires the SIM's PhoneAccount to advertise `CAPABILITY_ADHOC_CONFERENCE_CALLING`**. This is a distinct mechanism from merging two existing calls. See https://developer.android.com/reference/android/telecom/TelecomManager and https://developer.android.com/reference/android/telecom/PhoneAccount.
- Verified AOSP code explicitly gates this by `CarrierConfigManager.KEY_SUPPORT_ADHOC_CONFERENCE_CALLS_BOOL` **and** IMS registration: https://android.googlesource.com/platform/packages/services/Telephony/+/master/src/com/android/services/telephony/TelecomAccountRegistry.java . The AOSP static default is false; AT&T and the actual host firmware may or may not enable it.
- Incoming cellular calls already in progress cannot automatically be merged using only `startConference`; the normal merge and `Call.conference` controls are exposed through `InCallService` (default dialer or eligible wearable companion). The user explicitly rejects becoming the default dialer. Carrier call forwarding to a free VoIP number might cover inbound separately, but forwarding from AT&T to Google Voice can interact badly with linked-number loops and voicemail. This requires real tests. Google Voice free consumer service does not expose a public API to embed its audio into PhnLnk.
- Google Voice is a separate number/account and would probably require a separate app to receive its call. This violates the desired all-inclusive experience. AT&T may count two simultaneous cellular call legs against the plan; check billing.
- Even if outgoing ad hoc conference support exists, the path cannot be described as working until the host's exact SIM `PhoneAccount` capabilities and a controlled end-to-end two-way test are confirmed.

**Feasibility test that avoids dialing anyone:** A host-side status-only diagnostic could retrieve visible `PhoneAccount` capabilities from `TelecomManager`/carrier configuration (if permitted) and show whether `CAPABILITY_ADHOC_CONFERENCE_CALLING` is present. Do not attempt to originate two calls without the user's explicit permission. If the capability is missing, do not promise this route.

This is a fundamentally different route from privileged cellular PCM capture and deserves tracking, but is not yet a fit.

### Rechecked alternative APIs

- Google's Android Cross-device Services **Call casting** currently targets video calls in apps such as Google Meet, not the original AT&T cellular voice call: https://support.google.com/android/answer/14997291.
- Jetpack Telecom `CallsManager.CAPABILITY_SUPPORTS_CALL_STREAMING` applies to **VoIP calls owned by the application**, not unrestricted access to a separate carrier call: https://developer.android.com/reference/androidx/core/telecom/CallsManager.
- Android `MANAGE_ONGOING_CALLS` can provide companion `InCallService` control to apps paired with a physical wearable via `CompanionDeviceManager`. This provides call control, **not cellular PCM audio**; it is not a generic software-only grant to pair two remote Android devices: https://developer.android.com/reference/android/telecom/TelecomManager .
- Android audio sharing documents say an AccessibilityService can still **capture microphone input during a call**, but explicit capture of cellular uplink/downlink requires a privileged app with `CAPTURE_AUDIO_OUTPUT`. Acoustic speakerphone relay is fragile, can mute/silence streams, and cannot meet a reliable screen-off, privacy-preserving product target: https://developer.android.com/media/platform/sharing-audio-input .
- Legacy AT&T documentation describes NumberSync on certain **AT&T-compatible Android tablets**. The current May 20, 2026 AT&T consumer device list names Android **watches** as compatible, not arbitrary Android tablets. Treat old instructions as historical unless AT&T confirms eligibility for the user's existing exact devices: https://www.att.com/support/article-modal/wireless/KM1209875/ .

**Current status:** No verified complete route under all fixed conditions. Do not silently relax them.

