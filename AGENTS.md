# Instructions for coding agents and future PhnLnk maintainers

Read `MAINTAINER.md` and `docs/APP_ONLY_AUDIO_PLAN.md` BEFORE changing the app.

PhnLnk has a permanent product contract. Do not invent dependencies, promise capabilities that are unavailable, or reintroduce rejected setup requirements.

1. The user keeps the existing AT&T SIM, number and host Android phone. A secondary Android phone or tablet must eventually receive live cellular voice, without moving the number.
2. The intended end state must require only the two existing Android devices. No computer, Linux SIP gateway, dongle, ESP32, Bluetooth accessory, second mobile service, root, Shizuku, or recurring/manual debugging on every run. A single, optional guided Wireless Debugging + pairing workflow is now approved for audio feasibility tests only.
3. Do not ask the user to make PhnLnk the default Phone app unless actual two-way cellular audio on the remote Android device has been verified and they expressly approve the switch. The user is conditionally open to the role for a polished, fully functional solution, but the role by itself does not grant audio access. Keep the stock dialer intact for now.
4. A user-initiated guided pairing with Android Wireless Debugging is now allowed ONLY for optional host-side audio testing, never required for ordinary dialer functionality. Android must present the pairing code and the user must approve it; PhnLnk may remember the identity, retry while debugging is available, and explain when Android disables it. Never claim PhnLnk can toggle or approve debugging silently.
5. Android restricts raw cellular call audio. A standard third-party APK cannot bypass this using a hidden permission, MediaProjection or an InCallService. If no compliant route is verified, acknowledge the limitation. Never display a fake audio-connected state.
6. The current build is call control ONLY. Both audible speech directions, remote over internet, are unimplemented. Do not market as complete.
6. The companion pairing must be persistent until revoked. Never make users repeat a six-digit pairing process after routine app restarts.
8. PhnLnk should never light the host screen deliberately during a call. Android's independent behavior cannot be overridden by claiming a guarantee.
9. Do not send raw control HTTP or SIP traffic to the open internet. Any real remote calling must use encrypted, authenticated transport.
10. Preserve the Android application ID `com.richbeatty.phnlnk`. Keep increasing `versionCode`. NEVER publish a production APK signed with a throwaway/debug key. Permanent signing credentials are private GitHub Actions secrets, not source files.
11. Maintain the project openly and verify facts. Distinguish compiled, device tested, and production-ready in documentation. Never claim success on the MP01 without device evidence.
12. Keep features working: basic outgoing dial, incoming state, answer/end controls, contacts, favorites, pairing. Changes must not silently erase data, drop compatibility, or reintroduce OEM branding.
13. Before every push run `python3 tools/maintainer_check.py` and compile Android. Examine GitHub Actions logs. Check installation/upgrading with a matching signing key before announcing a release.
14. Document changes and confirmed regressions in `MAINTAINER.md` and `CHANGELOG.md`. Explain blockers without promising future deliveries.
15. Never commit passwords, signing keystores, ADB private keys, phone numbers, or call recordings.

A build passing CI does not demonstrate cellular voice audio. Respect Android and carrier constraints. Do not use experimental PhnLnk for emergency calls.
