# Known limitations and open work

This is the current source-of-truth list of missing or unproven behavior. It is written for users, maintainers and automated coding tools.

## Current feature matrix

| Feature | Status | Evidence needed |
| --- | --- | --- |
| Companion pairs with host | Implemented, previous basic user experience available | Test across app restarts and revocation |
| Place ordinary carrier call | Implemented through Android Telecom | Confirm across target ROMs |
| Answer and end carrier calls | Implemented through Android Telecom | Verify permissions and active-state handling |
| Contact search and favorites | Implemented using companion contacts | UI and permission tests |
| Recent companion requests | Implemented | Not a complete host call-log sync |
| Incoming alerts | Implemented by status polling | Background delivery and wake behavior test |
| Experimental local ADB pairing | Implemented | Android settings still require user approval |
| ADB helper startup | User previously reported helper ready | V2 recovery and reboot behavior still unverified |
| Alternative cellular capture routes | Implemented | Actual MP01 downlink speech not verified |
| Alternative cellular injection routes | Implemented | Caller hears speech/beep on MP01 not verified |
| Encrypted media TCP session | Implemented | Actual JVM protocol test must pass in CI |
| Companion microphone/speaker | Implemented | Real Android route and audio quality test |
| Fully usable two-way cellular voice | **Unverified** | Both people hear intelligible speech in a live call |
| IVR keypad without default role | Experimental in-band tone | Cellular network and IVR recognition |
| Automatic remote reconnect | Partial | Android, Wi-Fi and service policy behavior |
| Calls after screen off/reboot | Not validated | Repeated MP01 field test |
| Internet call relay | Not implemented | Secure discovery/relay or managed VPN integration |
| Control API encryption | Not implemented | TLS or equivalent authenticated transport |
| Emergency calls | Not supported | Use normal Phone app |
| Android default dialer role | Intentionally not requested | User must explicitly approve and benefit be proven |

## Critical open acceptance gates

### 1. MP01 cellular downlink

Earlier capture reported `SILENT_OR_MUTED` for the initial VOICE_CALL route. Alternative routes now exist in code, but there is no independent evidence showing the MP01 produces the far-end caller's actual speech. PCM transport cannot repair a zero or wrong audio source.

### 2. MP01 cellular uplink

An AudioTrack may initialize and accept samples while the carrier uplink remains silent. The far-end person must confirm audible speech or a consented test tone. Without that, outgoing companion audio does not work.

### 3. Simultaneous full duplex

Independent downlink and uplink diagnostics are not enough. A real conversation must succeed with the host phone's screen off, acceptable latency and natural turn-taking before the app can be advertised as a working second-device calling solution.

### 4. Reliable app lifecycle

Foreground microphone, host service, companion monitor, shell helper and Android Wireless Debugging can all stop or change state. Reconnection under screen lock, standby, reboot, task eviction and network changes requires controlled field testing and further implementation.

### 5. Safe remote connectivity

The app's AES-GCM voice channel is encrypted, but the host's call-control HTTP and initial pairing are not TLS protected. The app must not be publicly exposed. An independent private VPN is a possible transport, but PhnLnk does not provision, discover or manage one yet.

### 6. Call behavior beyond basics

Call waiting, conference calls, multiple simultaneous cellular call accounts, Bluetooth hands-free coexistence, VoWiFi, carrier switching between LTE and Wi-Fi, international calls, emergency dialing, outgoing presentation and unusual IVR tones are not comprehensively handled or verified.

## What is specifically not a solution

- Changing the default Phone app role does not magically grant cellular PCM access.
- Shell PING proves only a running helper, not call-audio capture.
- A JVM AES-GCM roundtrip proves protocol integrity, not audio route capability.
- Detecting nonzero RMS does not prove that the data came from the distant caller.
- Returning INJECT success does not prove the distant caller heard anything.
- Exposing public ports or disabling authentication is not an acceptable workaround for connectivity.
- Asking the user to uninstall and pair again for each code iteration is not an acceptable release process.

## Evidence-based next work

1. Finish automated protocol and error-path testing without requiring the user to install anything.
2. Diagnose all protocol and startup failures with stable stage codes and sanitized host-side messages.
3. Review the Android shell helper against public API availability, attribution and vendor audio routing constraints.
4. Acquire genuinely comparable MP01 firmware evidence or independent device access before making any claim about audio viability.
5. Once evidence justifies it, document a small, consented field validation rather than repeatedly sending speculative APKs.
6. If MP01 audio is impossible under the accepted permissions, explain that limitation and evaluate trade-offs openly rather than pretending a generic workaround is certain.

## Status change requirements

An item may move from device-unverified to working only with dated reproducible evidence, exact device/OS, expected and actual result, and confirmation of the specific capability. A maintainer must not infer this from source code, CI, README text or enthusiasm.

Updated alongside version 0.9.2.
