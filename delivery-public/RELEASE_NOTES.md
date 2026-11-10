PhnLnk 0.9.5 (versionCode 19) — Android pairing reliability repair

Source: 7dd1d846720070581345dfb0351e8487299f8842 (PR #3, based on main 198710f).
Android 14 runtime CI passed: https://github.com/richbeatty/PhnLnk/actions/runs/42859284168
Android 11/API 30 minimum; targets Android 14/API 34; compiled with API 35.

The signed APK is made from the exact unsigned release artifact produced by that successful CI run.

WHAT CHANGED
- Fixes a reproduced AndroidKeyStore/Conscrypt incompatibility that closes TLS before certificate discovery: opaque EC TLS signing requires NONEwithECDSA authorization, absent from the legacy host key.
- New host identities authorize the required raw operation. Existing identities remain untouched until explicit Repair encrypted connection confirmation. Repair retains the old key, saved pairing tokens and separate ADB identity, and serves only the selected new alias. Companions must explicitly verify the new fingerprint.
- Discovery errors identify HOST_TLS_DISCOVERY before any code is sent. No plaintext fallback or automatic certificate trust.
- Self-ADB discovery rejects other LAN devices' advertised ports, with local IPv4, scoped IPv6 and mapped-IPv4 regression coverage. Setup retains ADB_PAIR_HANDSHAKE details. Android-controlled debugging shutdown is not fixed or bypassed.
- Pauses additional audio backends and adds actual Android runtime proof as a required release gate. Keeps V4, native 48 kHz option, EOF fixes and encrypted media unchanged.

UPDATE AND RECOVERY
1. Install this same APK on BOTH devices over 0.9.4; do not uninstall or clear app data. The package and permanent APK signing certificate are unchanged; versionCode increases from 18 to 19.
2. On the main phone, open Manage connection and tap Repair encrypted connection if shown. Confirm the repair. This deliberately changes the HOST TLS fingerprint, not the APK signing identity. Saved device tokens and Android debugging pairing are retained.
3. If the companion was already paired, use Verify main phone security fingerprint in connection settings and compare ALL groups with the main phone. Do not forget the pairing merely to approve a new fingerprint. If this is the first companion pairing, generate a current main-phone pairing code and complete the normal fingerprint comparison.
4. Test basic pairing/status on the same trusted private Wi-Fi before attempting audio or VPN operation. If Android reports an APK signature mismatch, stop; do not uninstall to force the update.

VALIDATION AND LIMITS
53 distinct JVM/policy cases pass (TLS cases run for both protocol versions), plus seven real Android 14 emulator cases. Those reproduce legacy raw-sign denial and TLS closure; prevent silent rotation; verify explicit repair, token/ADB preference persistence, retained legacy key with active-alias selection, explicit pin migration and production HTTPS pairing/status using real AndroidKeyStore and HttpsURLConnection. Debug, instrumentation and release APK builds passed. Signing and published-file checks are separate.

The Android emulator does NOT prove MP01 cellular speech, hardware-specific behavior, the physical repair UI, long-term Wireless Debugging, actual signed update persistence, screen-off availability or different-network calls. Both MP01 audio directions remain unverified. Debugging may be turned off by Android after reboot/network changes; an ordinary APK cannot silently restore it. Do not keep repeating pairing codes when only reconnect failed.

No root, Shizuku, extra hardware, SIM/number/carrier change or default Phone role. Stock Phone remains default. Control/pairing stay pinned HTTPS on 47321; media remains authenticated/encrypted on 47322. No public port forwarding, built-in VPN or internet relay. Not an emergency-calling solution.

Methodology and evidence: https://github.com/richbeatty/PhnLnk/blob/7dd1d846720070581345dfb0351e8487299f8842/docs/RELIABILITY_RESET_2026-11-10.md

Signed locally with the owner's supplied permanent PKCS12. No private key or password is in the repository or release. Public certificate SHA-256:
eb184833077dac935052e595afd977baeb1a8b55a43d92a21c06a0c26be9f802

OWNER-CONFIRMED ADB LIMIT: Android itself switches Wireless Debugging off. The mDNS repair does not override that policy. Self-ADB is conditional research access and is not a proven foundation for unattended startup/recovery. Establish a durable authorized MP01 audio path, potentially with OEM-supported integration, before promising reliable remote cellular speech.
