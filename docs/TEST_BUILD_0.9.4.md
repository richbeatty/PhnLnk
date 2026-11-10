# 0.9.4 owner-requested test candidate

The owner explicitly requested a new APK on November 10, 2026. This supersedes the earlier request not to send speculative builds for this candidate. **Neither direction of cellular speech on MP01 is verified.** The new option tests a specific format hypothesis grounded in the published MediaTek policy; it is not a promise of working remote calls.

## What changes

- Version 0.9.4 / versionCode 18, same package `com.richbeatty.phnlnk` and permanent signing configuration.
- V4 helper retains V3 prepared/pumped Looper, shell attribution, pre-READY errors and binary PCM guard. Authenticated V1/V2/V3 replies now trigger replacement; V4 is reused. Android ADB pairing is retained.
- New **Outgoing audio: TELEPHONY_48K** advanced choice requests 48 kHz stereo PCM16 directed toward Telephony Tx while Android reports MODE_IN_CALL. Published MT6789 incall playback declares this format. The installed firmware may still reject, silence or locally route it.
- A stateful 97-tap Blackman-windowed sinc adapter converts 16 kHz mono to 48 kHz stereo, with unity DC gain per phase, saturating PCM16 and 1 ms group delay. Each 640-byte / 20 ms input frame becomes 3,840 native playback bytes. Wire framing, encryption, capture backends, AUTO selection order and all other uplink choices remain unchanged.

The new choice is explicit; AUTO does not silently select it. The adapter adds no privileges and cannot repair a missing modem route. Public mix-port stereo and Telephony Tx mono declarations still require Android/HAL conversion; a successful track write is not proof that the caller hears it.

## Update and signing

Only install a release signed with the existing permanent certificate and a higher versionCode. Do not uninstall to bypass a certificate mismatch. The missing key cannot be reconstructed from an APK or repository source. A debug build or unsigned candidate is not an update-compatible substitute. The repository's signed-release workflow refuses publication if permanent signing secrets are missing.

Update both host and companion together. Coming from pre-0.9.3, compare the complete host HTTPS fingerprint when prompted; saved pairing tokens are retained. No plaintext fallback is provided. Android Wireless Debugging authorization is independent of this HTTPS verification and should not need a new ADB pairing code if it remains valid.

## Focused test after a signed build is available

1. Keep both devices on the same trusted Wi-Fi first. Keep the regular Phone app as default and leave host call output unmuted. Have a consenting participant ready for an ordinary cellular call.
2. On MP01, open **Guided audio setup**, enable Android Wireless Debugging if needed, and tap Retry. Existing ADB pairing should be reused. A READY helper is not a successful audio test.
3. Under the audio test choices, set **Incoming capture: DOWNLINK** and cycle **Outgoing audio** to **TELEPHONY_48K**. Place or answer the cellular call normally before testing the native route.
4. With the other person's agreement, tap **Play outgoing test tone**. Record whether the person actually hears the beep and the displayed result/error. A local beep or WROTE_SHORTS alone is not a pass.
5. Start voice on the paired companion. Check far-end speech at the companion and companion speech at the far end separately, with host microphone acoustically isolated. Then test simultaneous speech and companion mute. If downlink fails, record it separately; do not attribute both directions to the new uplink option.
6. Stop voice and end the call. Confirm the companion microphone notification ends and ordinary host calls remain usable. Restore AUTO if the native route fails; no new pairing or uninstall is needed.

Report only app version on both devices, selected methods, sanitized error/result text, and who could hear whom. Do not post numbers, contacts, codes, keys, tokens or recordings. Do not configure public port forwarding. A separately configured private VPN and different-network test follow successful local speech; PhnLnk does not provision a VPN.

## Automated evidence

The production adapter and helper-version parser are included in `bash tools/test_voice_protocol.sh`. Eight added cases verify exact duration/stereo length, arbitrary chunk boundaries, duplicate channels, full-scale DC gain, impulse support/1 ms delay, 1 kHz amplitude with 15/17 kHz images below -40 dB, full-scale transition/session isolation, and V1/V2/V3 replacement with V4 reuse. These tests use no Android PCM mocks for conversion; they do not execute AudioTrack, the HAL or a carrier call.

Existing 22 transport, 9 relay, 5 TLS (each protocol version) and 7 policy cases remain required. Shell DEX and Android debug/release compilation, bundled helper integrity and permanent-signature comparison are separate gates. See [acceptance plan](TEST_PLAN.md).

## November 10 local validation and signing result

Maintainer checks, all 51 distinct automated cases (TLS repeated for TLS 1.2 and TLS 1.3), V4 helper DEX, Android debug and unsigned release builds passed. Both APKs contain the exact freshly built V4/native-adapter asset. Build used JDK 17, Gradle 8.9 and Android API 35. Package/version inspection confirms 0.9.4/code 18.

The [signed 0.9.3 workflow attempt](https://github.com/richbeatty/PhnLnk/actions/runs/42844615967) passed compilation, then failed the permanent-credentials gate. No signed APK was published. No release keystore was found in this workspace or attached files; the owner is unsure of its location. The 0.9.4 candidate is therefore **built but not signed or installable as a compatible update**. Restore the original private PKCS12 (alias phnlnk) through GitHub Actions secrets PHNLNK_KEYSTORE_B64 and PHNLNK_STORE_PASSWORD. Do not paste them into chat, commit them, substitute a new key, or uninstall to bypass the missing identity.
