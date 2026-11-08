# PhnLnk

PhnLnk is an Android host and companion project for using your existing phone number on a secondary Android device. It preserves the host phone's normal Phone app, SIM, carrier, and number.

**Read the permanent [maintainer contract](MAINTAINER.md) and [agent instructions](AGENTS.md) before changing code.** The user's restrictions are now enforced partly by CI to prevent backtracking.

## Interface update

The v0.7 native interface separates main-phone setup from companion calling. The companion now has a prominent keypad, a distinct live call-status panel, contacts, favorites, recent requests, and collapsible connection settings. The main phone has a high-contrast pairing and connection dashboard. These are usability improvements; **two-way voice audio on the companion is still unavailable**.

The user is open to considering the default Phone app role only if it truly enables a complete calling experience. Android's public default-dialer APIs do not provide cellular call audio capture or injection, so PhnLnk does not ask for that role just to access a prettier call screen.

## Troubleshooting Wireless Debugging connection

If guided audio setup says **Connecting to Android** and does not proceed, v0.8.1 now shows the specific phase, or reports a stall after 45 seconds. Check that Wireless Debugging is on, the phone is on Wi-Fi, and **PhnLnk** appears under **Wireless Debugging > Paired devices**. Tap **Retry saved connection**.

If automatic port discovery fails, open Android **Wireless Debugging** and copy the connection port shown beside the device's IP address (for example `192.168.1.5:39817` means enter only `39817`). Do NOT enter the temporary pairing port from the six-digit pairing-code popup. In PhnLnk's guided audio screen enter that connection port and select **Save port and retry**. This changes only the local ADB connection, not your phone number or carrier.

If Android no longer lists PhnLnk under Paired devices, use **Pair again if Android forgot this app**. If Wireless Debugging turned off, Android requires you to turn it back on. **No debug authorization or cellular audio is bypassed.**

Even if the local helper reports ready, two-way cellular audio on the second Android device is not yet available.

## What works

- The host phone can request outgoing cellular calls through Android Telecom without becoming the default Phone app.
- The companion Android app has a dial pad, contacts, favorites, recent requests and incoming-call status.
- The companion can send answer and hangup requests to the host over a trusted private LAN.
- Paired companion devices remember their access until explicitly disconnected or revoked.

## What does not work yet

**PhnLnk does not carry cellular call audio to your secondary Android device.** A normal Android app cannot capture and inject both sides of a carrier voice call through Android's public APIs. Merely becoming the default Phone app does not grant call audio access. The user still rejects Shizuku, root, extra hardware, number porting, and carrier switching. They have explicitly permitted an optional guided one-time Wireless Debugging pairing to test audio access; it does not make remote voice work.

PhnLnk does not ask you to become the default Phone app or enable debugging during ordinary call-control setup. **v0.8 adds an optional Guided audio setup** on the main phone, with one-time Android-approved Wireless Debugging pairing, automatic retry when debugging remains available, visible connection status, a stop control, and consent-gated live-call tests.

On the host, open **Guided audio setup** and tap **Start guided setup**. PhnLnk asks for notification permission and opens Developer options. Manually enable Wireless Debugging, select **Pair device with pairing code**, and enter the code through PhnLnk's notification while the Android dialog stays open. Pairing is remembered. Once authorized, PhnLnk retries its local shell helper after app restarts and best-effort after reboot. If Android turns Wireless Debugging off, you must enable it in Settings again. This is an experimental shell helper, **not a working audio relay**.

**Live in-call keypad tones** from the companion cannot be promised while retaining the stock Phone app, because ordinary app call controls do not offer the necessary live DTMF APIs. The number pad still enters numbers before making calls.

The current HTTP call-control service is limited to a trusted local network and is not a secure internet calling service. Never forward port 47321 publicly. Do not use PhnLnk for emergency calls.

See [app-only audio research](docs/APP_ONLY_AUDIO_PLAN.md) | [complete number-sharing and Beeper research](docs/CALL_RESEARCH_2026.md) for the technical investigation. Working two-way voice is a hard release gate, not a checkbox satisfied by an APK compilation.

## Getting started with the current prototype

1. Install the APK from a PhnLnk [GitHub Release](https://github.com/richbeatty/PhnLnk/releases) on the host and secondary Android devices.
2. On the main phone choose **This phone has my SIM**. On the companion choose **Connect to my other phone**.
3. On the host tap **Pair a device**, then use the displayed QR code or six-digit pairing code on the companion.
4. The companion remembers the pairing. Calls are controlled on the main phone, but audio stays on that phone.

Releases before v0.6.1 are experimental and have not been fully validated on the intended phone. Do not assume an old APK is safe or a finished audio solution.

## Reliable updates and release signing

The earlier APKs were produced with automatically generated debug keys on GitHub runners. **Android refuses an in-place update if the new APK is signed by a different key.** That is the reason for the installation conflicts.

The signing fix is now enforced in CI: ordinary pushes build **debug artifacts only**. GitHub Releases can only be published from the manually triggered workflow with the project's **same permanent private signing certificate** loaded from encrypted repository secrets. Missing secrets make the workflow fail. No unsigned or throwaway-key release will be published.

The private keystore was prepared for this project outside GitHub. It still needs its two existing values saved under **Settings > Secrets and variables > Actions > New repository secret**:

- `PHNLNK_KEYSTORE_B64`: Base64-encoded PKCS12 signing keystore, alias `phnlnk`.
- `PHNLNK_STORE_PASSWORD`: The matching keystore password.

The owner must add these secrets to GitHub; the connector cannot manage GitHub Actions secrets from this chat. After saving them, run the **Android CI and signed releases** GitHub Actions workflow on `main` manually. The workflow builds a signed APK, verifies the signature using `apksigner`, and then publishes the release.

**One final uninstall will still be required** to move from a prior differently signed debug APK to the first permanently signed release. Subsequent releases signed with the retained certificate and a higher versionCode can update in place without wiping pairings.

Do not publish or commit the private keystore or password. Back both up securely.

## Build and maintenance

Requires JDK 17, Android SDK 35 and Gradle 8.9.

```sh
python3 tools/maintainer_check.py
bash shellprobe/build.sh
gradle --no-daemon :app:assembleDebug
```

The workflow runs the same regression checks on each push. A successful build is not proof of carrier audio access or device compatibility.

## Licensing

PhnLnk's original source is MIT. ZXing is Apache 2.0. The optional experimental ADB source uses the upstream dependencies under their respective licenses. Historical Linux/SIP approaches have been rejected under the current product contract.
