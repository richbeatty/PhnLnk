# PhnLnk

A device-neutral Android experiment for controlling calls on a host phone from a second Android device or desktop browser, using the host's existing cellular number.

[Download the latest APK](https://github.com/richbeatty/PhnLnk/releases)

## Setup

On the phone with your cellular SIM, choose **This phone has my SIM**. On the second Android device, install the same APK and choose **Connect to my other phone**. On the host, tap **Pair a device** and scan the QR code with the secondary Android device, or enter the local IP and six-digit code.

Pairing is remembered until you explicitly disconnect or revoke it. If the host's Wi-Fi IP changes, use **Update host Wi-Fi address** on the companion. You do not need to pair again simply because the IP changed. The host and companion services now attempt to resume after a phone restart where Android permits foreground services.

For a desktop PC, use the address displayed on the host and enter the six-digit code. The browser remembers its pairing.

## Call features

- Numeric dial pad for dialing.
- Search local contacts on the secondary Android device.
- Favorites and recent requests.
- Incoming call status and user-enabled notifications on the companion.
- Remote request to dial, answer, or end cellular calls.
- **New: optional in-call keypad tones** on the secondary Android and browser. During an active call, pressing a keypad button sends its tone to the host, but only when the host is the Android default Phone app and its InCallService is active.

### Turn on in-call keypad tones

On the **host** open PhnLnk and choose **Enable in-call keypad**. Android asks whether to make PhnLnk your default Phone app. This is required by Android to access the call's DTMF functionality. It is optional and requires explicit approval. You can change back under Android **Settings > Apps > Default apps > Phone**.

**Please test ordinary incoming and outgoing calls on your phone before relying on the new dialer role.** The experimental PhnLnk in-call service provides an incoming call notification, answer/end controls, and an in-call keypad. It does not intentionally turn on the screen, show over the lock screen, or open full-screen activities automatically. Android or the manufacturer may still wake the screen when a call arrives. This is best-effort screen behavior, not a promise that the display will stay off.

Without approving default Phone app, normal remote **Dial**, **Answer**, and **End call** controls remain available as before. In-call keypad tones are unavailable.

## The important limitation

**PhnLnk does not yet carry two-way cellular audio to a secondary device.** A second device can send commands but it cannot take part in the voice conversation. Answering a call still leaves the audio on the host phone. The Android APIs for injecting/capturing cellular audio are restricted. No normal-app-only solution has been verified on the user's host device. This release does not require Shizuku or root.

PhnLnk operates on the same private local network and uses an HTTP server with bearer-token authentication. **The traffic is not encrypted.** Do not expose port 47321 to the internet or use an untrusted network. It is not a substitute for the system phone app for emergencies.

## Signing and updating without uninstalling

Previous GitHub Actions builds used a *different automatically generated debug certificate for each build*. Android correctly refuses to update an app signed by a different certificate. A later build cannot fix or impersonate the existing lost certificate.

From v0.4 onward, the build can use one permanent signing key provided through encrypted GitHub Actions secrets:

- `PHNLNK_KEYSTORE_B64`: Base64 of a PKCS12 keystore with alias `phnlnk`.
- `PHNLNK_STORE_PASSWORD`: The keystore password, also used for the key.

Set these under **GitHub > repository Settings > Secrets and variables > Actions**. There must be no keystore or password committed to this public repository. When both are set, GitHub Actions builds a signed release APK using the permanent key. Future releases signed with that same key and package ID can install over one another.

**You will still need one final uninstall** moving from a previous temporary debug-signed release to the first permanently signed release. Pairings stored inside the old installation will be lost during that uninstall. Once the permanent signing key is configured, future update installs can preserve app data.

If the GitHub Actions signing secrets have not been set, the workflow produces another **development debug preview**, clearly identified as such in its release notes. It is not a permanent upgrade path.

Back up your signing key securely. Losing it again means Android cannot install updates over existing installations.

## Technical and privacy notes

- The host's pairing tokens are persisted across process restarts. The six-digit pairing code is short-lived and single use; paired devices receive individual stronger tokens.
- The host attempts to recover after `BOOT_COMPLETED` or app replacement. Android battery policies and foreground-service restrictions can prevent unattended recovery. Test after reboot.
- The incoming-call alert service also attempts to recover after restart when enabled.
- The host does not intentionally acquire a screen wake lock or launch its in-call UI on incoming calls. The OS can still wake the screen.
- The remote dialer only permits ordinary +1 US/Canadian phone numbers and blocks 900 numbers; never rely on this app for emergency calling.
- Contacts remain local on the second Android device.

## Build

JDK 17, Android SDK 35, Gradle 8.9:

```sh
gradle :app:assembleDebug
```

For release signing, provide `PHNLNK_KEYSTORE_FILE` and `PHNLNK_STORE_PASSWORD` environment variables and run `gradle :app:assembleRelease`.

## License

PhnLnk original code: MIT. QR encoding: ZXing core (Apache-2.0). No third-party privileged audio gateway code is included.
