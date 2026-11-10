# MP01 cellular audio feasibility, November 8, 2026

**Verdict: unresolved on the installed MP01 firmware.** There is a credible shell-authorized research path, but no demonstrated two-way speech on this phone. Neither universal impossibility nor compatibility follows from the evidence. No new install or speculative APK test is requested.

November 10 follow-up: [resumed engineering status](ENGINEERING_STATUS_2026-11-10.md) traces Android 14 explicit telephony routing and native format selection. Both pinned public policies were downloaded again and match the prior hashes. [Native comparison evidence](research/MP01_NATIVE_FORMAT_REVIEW.json) distinguishes mix declarations from HAL/device formats; it is not installed-firmware evidence.

## What is established

The owner reported outgoing call control working, the shell helper answering PING, an original VOICE_CALL measurement of `SILENT_OR_MUTED`, and a later unspecified EOFException. No trace, installed vendor policy, build fingerprint, or independently reproducible MP01 speech test is available. The execution environment has no connected Android device. The Android app builds and transport tests are separate evidence.

## Android 14 privilege and routing chain

| Layer | Requirement | What success proves |
| --- | --- | --- |
| Ordinary APK / Telecom | Runtime call-control permissions | Requests calls, answer and hangup; no cellular PCM entitlement |
| Default dialer | Dialer role and InCallService | Call objects and DTMF; not privileged modem audio |
| Optional ADB helper | User-approved debugging, UID 2000 | Authorized shell process, not HAL support |
| AudioRecord input | Recording/app-op attribution, CAPTURE_AUDIO_OUTPUT or applicable interception path | Capture can be requested; output may still be muted |
| Call redirection APIs | CALL_AUDIO_INTERCEPTION, active call mode, exposed telephony ports | Framework eligibility; no guarantee of intelligible speech |
| Telephony AudioTrack | Appropriate policy route and permission checks, including MODIFY_PHONE_STATE for telephony Tx in AOSP | Accepted output; not proof the modem transmits it |
| AudioPolicyManager / AudioFlinger / HAL | Policy profiles, routes, native streams, vendor modem speech implementation | Only a real speech test establishes the required end-to-end result |

Android 14 declares CAPTURE_AUDIO_OUTPUT, MODIFY_AUDIO_ROUTING, MODIFY_PHONE_STATE and CALL_AUDIO_INTERCEPTION as protected permissions. AOSP's Shell package requests these for platform testing; an OEM can change actual grants. A helper does not acquire them just by reflection or by changing a package-name string. Calling identity remains shell UID, and attribution must truthfully match it.

The Android 14 AudioManager extraction/injection helpers are hidden system/test APIs. They reject a non-call audio mode and a false `isPstnCallAudioInterceptable()` result. The latter checks for telephony device ports, not live speech. AudioPolicyService separately validates attribution and permissions. AudioFlinger creates and schedules tracks; it cannot manufacture a missing vendor modem route. The installed HAL may expose a nominal stream while returning digital zeros or routing playback locally.

Sources, pinned to Android 14:

- [Protected permission declarations](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/core/res/AndroidManifest.xml)
- [Shell permission requests](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/packages/Shell/AndroidManifest.xml)
- [AudioManager call extraction and injection](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/media/java/android/media/AudioManager.java)
- [AudioService eligibility check](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/services/core/java/com/android/server/audio/AudioService.java)
- [AudioRecord context and attribution handling](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/media/java/android/media/AudioRecord.java)
- [AudioPolicyService permission enforcement](https://android.googlesource.com/platform/frameworks/av/+/android-14.0.0_r1/services/audiopolicy/service/AudioPolicyInterfaceImpl.cpp)
- [AudioPolicyManager routing, INCALL_MUSIC and profiles](https://android.googlesource.com/platform/frameworks/av/+/android-14.0.0_r1/services/audiopolicy/managerdefault/AudioPolicyManager.cpp)
- [Default dialer permission sets](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android14-release/PermissionController/res/xml/roles.xml)

## Independent MP01 source evidence

Minimal publishes [mp01-device](https://github.com/minimalcompany/mp01-device/tree/1dff2f7fa804af63e8c9c5f5ddaae6af4a44b74a). At that revision:

- [Z10 ProjectConfig](https://github.com/minimalcompany/mp01-device/blob/1dff2f7fa804af63e8c9c5f5ddaae6af4a44b74a/device/mediatekprojects/Z10/ProjectConfig.mk) identifies MT6789 and enables `MTK_PHONE_CALL_FIXED_48K_SAMPLERATE`.
- [MT6789 policy](https://github.com/minimalcompany/mp01-device/blob/1dff2f7fa804af63e8c9c5f5ddaae6af4a44b74a/device/mediatek/mt6789/audio_policy_config/audio_policy_configuration.xml) declares an `incall_music_uplink` source with INCALL_MUSIC, 44.1/48 kHz stereo profiles and a route into Telephony Tx. Its Telephony Tx device profiles are 44.1/48 kHz mono. Voice Call In connects to the `voice tx` capture mix, supporting 8/16/32/44.1/48 kHz mono.
- A [vendor/common policy](https://github.com/minimalcompany/mp01-device/blob/1dff2f7fa804af63e8c9c5f5ddaae6af4a44b74a/device/mediatek/vendor/common/audio_policy_config/audio_policy_configuration.xml) differs: its Telephony Tx route lists hardware microphone sources without a software playback mix. Therefore selecting any common XML from the tree is insufficient.
- The [common build rules](https://github.com/minimalcompany/mp01-device/blob/1dff2f7fa804af63e8c9c5f5ddaae6af4a44b74a/device/mediatek/common/device.mk) select among project, base-project, platform and common policy files. Which artifact is installed on the owner's Android 14 build has not been established.

**Important alias:** `AUDIO_DEVICE_IN_VOICE_CALL` is the same native device value as `AUDIO_DEVICE_IN_TELEPHONY_RX`, not evidence of an absent telephony Rx port. See [Android 14 audio device enums](https://android.googlesource.com/platform/system/media/+/android-14.0.0_r1/audio/include/system/audio-hal-enums.h).

PhnLnk's helper currently requests **16 kHz mono** playback. That differs from the declared native incall playback profile. Android may convert formats for some output paths; this mismatch alone does not prove failure. A future policy-matched backend may need native 48 kHz stereo playback and an explicit resampling adapter while retaining the 16 kHz mono wire protocol. Do not change the wire format or silently force an unverified native route on this evidence alone.

The offline inspector makes these declarations reproducible without running an APK:

```bash
python3 tools/inspect_audio_policy.py /path/to/audio_policy_configuration.xml
python3 tools/test_audio_policy.py
```

It reports devices, mix profiles, routed software outputs and unresolved includes. It never equates declarations with audibility. The public source revision is not a dump of the owner's installed firmware. Minimal also describes continuing reliance on older MediaTek vendor code in its [July 2026 engineering update](https://minimalcompany.dev/blog/).

The [generated policy summary](research/MP01_POLICY_SUMMARY.json) preserves both source URLs, downloaded-byte SHA-256 hashes and the inspector output, explicitly marking the installed-firmware match as unknown.

## Concrete software defects repaired in 0.9.3

The shell helper previously called `ActivityThread.systemMain()` from a socket worker without preparing a Looper. [Android 14 ActivityThread](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/core/java/android/app/ActivityThread.java) constructs `H extends Handler` during construction. This can fail before any capture backend is tried while PING continues to answer. V3 prepares the main Looper, initializes and caches its context there, pumps callbacks, and performs socket work separately. Bootstrap failures remain explicit pre-READY errors.

The relay also used try-with-resources around the remote socket followed by a catch block that attempted to send an encrypted startup error. Java had already closed the socket at that point. The socket now stays open until the error attempt completes. Production relay tests reproduce helper refusal and early closure and verify receipt of the encrypted error.

These defects are independently demonstrable. Without the original device trace, neither can be named as the proven cause of the owner's particular EOF incident. Neither repair proves speech access.

## External project evidence and limits

| Project | Relevant observation | Limit for PhnLnk |
| --- | --- | --- |
| android-voice-sip-agent | Both redirect and direct telephony implementations; explicit shell attribution | Uses Shizuku/PBX; study underlying shell mechanisms, do not add those dependencies |
| Independent gateway field report | Initially silent MediaTek downlink and inaudible redirect uplink despite green checks; follow-up reports direct mode with local output mute off working on five phones, including MediaTek | No MP01; reported 30-second trials are not PhnLnk acceptance |
| JemRec | Local ADB bootstrap and shell recording; modem/mixer route selection changes with call mode | Recording and answering-machine behavior on other devices do not establish simultaneous remote full duplex |
| scrcpy | Prepared Looper, shell attribution and AudioRecord capture; configurable call sources | Forwarding captured audio is not cellular uplink injection |
| ShizuCallRecorder | Shell-attributed capture and vendor-specific fallback handling | Call recorder, not evidence of modem uplink injection; Shizuku itself remains excluded |

Primary project references:

- [Gateway audio implementation at reviewed revision](https://github.com/sparkcyf/android-voice-sip-agent/blob/5105a1f447ffa25ba32d0620dbeef44bbc1705ce/privileged/src/main/kotlin/io/github/sparkcyf/voicesipagent/privileged/shell/CallAudioTap.kt)
- [Original field report](https://github.com/sparkcyf/android-voice-sip-agent/issues/1) and [October 16 follow-up with two-way direct audio](https://github.com/sparkcyf/android-voice-sip-agent/issues/1#issuecomment-6218873530)
- [JemRec phone capture](https://github.com/jemcik/JemRec/blob/32af75cded0f6e0d6ec6a65a6f65a889a2b40cd0/shellserver/src/com/jemcik/jemrec/shell/PhoneCapture.java)
- [scrcpy server bootstrap](https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/Server.java) and [audio documentation](https://github.com/Genymobile/scrcpy/blob/master/doc/audio.md)
- [ShizuCallRecorder shell capture](https://github.com/kitsumed/ShizuCallRecorder/blob/4db9c1fe690f49cf37f5a7fd16b0f91dd4a3104e/app/src/main/java/com/kitsumed/shizucallrecorder/services/shell/recording/capture/AudioRecordCapture.kt)

Recommendation: retain the direct DOWNLINK/TELEPHONY research option and leave host call output unmuted. Match the installed policy and native profiles before more route changes. Android's helper APIs also construct their own AudioRecord/AudioTrack builders without the caller's explicit context: wrapping AudioManager's Context alone does not guarantee their final attribution. This remains a separate compatibility check.

## Phone Link and Bluetooth

[Microsoft requires Bluetooth for Calls](https://support.microsoft.com/en-us/windows/apps/phonelink/setting-up-calls-in-the-phone-link). HFP has a cellular audio gateway on the SIM phone and a nearby hands-free endpoint. The gateway's system stack handles modem audio. Android's [BluetoothHeadsetClient](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android14-release/framework/java/android/bluetooth/BluetoothHeadsetClient.java) is hidden/system API and relevant operations require BLUETOOTH_PRIVILEGED. A normal APK cannot assume that role is enabled in the companion firmware or that SCO PCM is exposed. Public BluetoothHeadset controls the gateway side, not an app-owned headset endpoint.

A raw RFCOMM connection or shell-granted Bluetooth permission is not an HFP implementation. An Android system headset client would still be nearby, would need firmware/profile support and a supported SCO audio path, and Bluetooth alone cannot span miles. No demonstrated software-only two-stock-Android cross-network HFP substitute was found. No third gateway is proposed under current constraints.

## Evidence required to close the MP01 question

Independent developer/OEM access should establish the installed build identifier, actual shell grants, package attribution, active audio mode, selected policy XML and includes, native input/output profiles, and actual AudioFlinger/HAL route during a consenting real call. Keep raw dumps private: they may contain numbers and identifiers. Compare a far-end-only spoken phrase with host microphone isolation and verify companion speech at the far end, then require simultaneous speech and a negative mute control. See [Test plan](TEST_PLAN.md). A sample counter, green capability flag, RMS value or successful write is insufficient.

If the installed policy lacks a permitted software-to-Telephony-Tx route, or the HAL rejects/zeros the only authorized path, an ordinary APK cannot repair the vendor graph. If the graph exists but the native format differs, investigate a format adapter first. If attribution or shell grants are missing, name that exact denial rather than blaming the HAL.

**Smallest requirement change if firmware blocks the path:** manufacturer-supported firmware/system integration that exposes authorized downlink and uplink while keeping the same two devices, SIM, carrier, number and stock Phone app. This requires OEM cooperation and might include a privileged service/HAL change; it is not self-grantable by PhnLnk. Root alone would not guarantee a missing HAL route. Alternatives such as an additional HFP gateway or carrier/hosted voice service each change an explicit constraint and have not been selected, purchased or configured.
