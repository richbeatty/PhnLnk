# Reliability reset: Android runtime evidence before another audio iteration

The owner reported that 0.9.4 closes the connection on **Connect phone**, with both devices updated, no fingerprint dialog and **Host: Ready** on MP01. Wireless Debugging also repeatedly fails. The owner asked to rethink the methodology. These are distinct trust/transport layers, not evidence that the MediaTek audio experiment failed. No phone reset, uninstall, default Phone role, new hardware or new signing identity is part of this change.

## Concrete TLS defect

The 0.9.3/0.9.4 AndroidKeyStore EC key authorizes SHA256 only. Android 14 Conscrypt delegates opaque EC TLS signatures through `NONEwithECDSA`, because it already hashed the TLS transcript in native code. That operation requires DIGEST_NONE authorization. A key that can sign an ordinary SHA256 message can therefore fail the actual TLS handshake before the companion can show the certificate. A bound SSL listener is insufficient to label encrypted connectivity ready.

Pinned sources:

- [Conscrypt CryptoUpcalls raw EC signing](https://android.googlesource.com/platform/external/conscrypt/+/refs/heads/android14-release/common/src/main/java/org/conscrypt/CryptoUpcalls.java#59)
- [AndroidKeyStore NONEwithECDSA digest authorization](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/keystore/java/android/security/keystore2/AndroidKeyStoreECDSASignatureSpi.java#48)
- [NativeSsl opaque-key integration](https://android.googlesource.com/platform/external/conscrypt/+/refs/heads/android14-release/common/src/main/java/org/conscrypt/NativeSsl.java#233)

A local Conscrypt/opaque-key reproduction closes the handshake with raw signing denied and succeeds with it allowed. The original SunJSSE and exportable-key Conscrypt suites both pass, exposing a validation gap rather than disproving the Android failure. The owner's exact device trace remains unavailable; the new Android runtime test is the platform-level confirmation gate.

## Explicit identity recovery, not silent trust changes

Key authorizations cannot be expanded in place for a non-exportable AndroidKeyStore key. Version 0.9.5 therefore keeps the legacy v1 alias until the host owner explicitly confirms **Repair encrypted connection**. Repair creates a separate v2 key with NONE and SHA256 authorization and retains the old alias, saved bearer-token hashes, companion token and separate Android ADB identity. Existing companions must explicitly compare and trust the new host fingerprint. The permanent APK signing key is independent and unchanged.

Fresh installations create the corrected v2 identity. Listener startup refuses a legacy key lacking raw-sign authorization, with HOST_TLS_KEY_NEEDS_REPAIR, rather than claiming a functional HTTPS host. The key manager serves only the selected identity, so retaining the old alias cannot cause it to serve the old broken key. Repeating repair does not rotate an existing v2 identity. Normal requests never regenerate keys or accept a new pin automatically.

Discovery failures now carry HOST_TLS_DISCOVERY and the exception type, and explicitly state that no pairing code has been sent. No HTTP fallback is added.

## Wireless Debugging is a separate conditional capability

The owner subsequently confirmed the actual symptom: **Android switches Wireless Debugging off**. This is an architectural limitation for unattended recovery. The mDNS fix below is independent and must not be described as fixing Android's policy. An already running detached helper might survive in some circumstances, but startup after reboot/process death remains conditional and unverified. A durable authorized MP01 audio path, potentially OEM-supported, is required before promising unattended remote calls.

The source also selected the first mDNS ADB port advertised anywhere on the LAN and applied it to 127.0.0.1. An unrelated device's advertised port can consequently produce a local failure. Discovery now checks the advertised address against this phone's interface address bytes. It ignores IPv6 scope identifiers and normalizes IPv4-mapped addresses, avoiding the earlier getByInetAddress scope problem. Connections remain loopback-only; the manual connection-port fallback remains available. Unknown/foreign addresses fail closed. OEM advertisements that do not match an interface may still need the manual fallback.

Added JVM cases cover foreign-service rejection, own IPv4/loopback, scoped IPv6 and mapped IPv4. Pairing-handshake failures now retain ADB_PAIR_HANDSHAKE and the exception type in the visible setup state, rather than erasing everything into a generic code warning. This is a software repair, not a diagnosis of every reported ADB failure. Do not clear or regenerate ADB pairing just because reconnection fails.

Android can turn Wireless Debugging off after reboot, Wi-Fi changes or policy changes. An ordinary APK cannot silently restore it. Reconnect improvements cannot turn that platform restriction into an always-available audio service. Treat self-ADB as conditional research access until repeated target-device lifecycle evidence establishes its actual limits.

## New validation and release order

1. Keep the existing code and native audio experiments, but pause additional audio backend/UI work.
2. Require the actual Android 14 KeyStore, Conscrypt, socket server, HttpsURLConnection, pairing persistence and explicit identity migration paths to pass before publishing another APK.
3. CI builds a custom debug-only instrumentation APK and runs seven isolated tests on a disposable API 34 emulator. Fixtures reproduce the restricted legacy key, verify denial and pre-certificate TLS failure, prevent silent rotation, perform explicit repair while preserving identities/tokens, ensure v2 is served with v1 retained, enforce pin verification and complete real production HTTPS pairing/status. Test fixtures must never run against an owner's installation.
4. The runtime step is ahead of signing/publication in the same job; inability to boot an emulator or any test failure blocks release. This workspace's attempted local emulator cannot boot with the available KVM/storage configuration. It is not recorded as an Android test pass. GitHub hosted KVM runs the required runtime gate.
   The fixture uses an explicitly enabled emulator Wi-Fi connection, waits for an assigned private IPv4 address and never hard-codes a guest address. Fixture setup refuses physical hardware before changing Wi-Fi or keys. This is test infrastructure, not a production feature to change Android debugging settings. Signed releases must use the exact unsigned artifact from a passing CI run, with artifact digest and embedded source revision checked.
5. After control works, validate local ADB discovery, explicit pairing, bounded reconnect and helper replacement separately. Keep OS-controlled failures visible and never equate PING with speech.
6. Require independently isolated far-end downlink, companion uplink, simultaneous speech and negative mute controls on the installed MP01 before evaluating mobile-data/private-VPN calling.
7. Only then invest in VPN lifecycle and eventually integrated rendezvous/media. Neither a new audio codec nor a network relay repairs inaccessible modem PCM.

The seven Android cases do not establish MP01 HAL compatibility, Wireless Debugging durability, physical UI behavior, a permanently signed update or carrier speech. If stock firmware provides no durable authorized audio path under the existing constraints, OEM-supported system integration remains the justified route to investigate. No excluded workaround has been introduced.
