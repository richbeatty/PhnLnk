# Setup and use

**Read first:** PhnLnk is an experimental two-device call controller and voice transport. Actual cellular audio on the MP01 is not yet confirmed. The user has asked not to be sent another speculative device-testing cycle. These instructions document the intended workflow; they are not a request to install or test the prototype.

## Requirements

- Main Android phone already contains the user's existing SIM and number.
- A second existing Android phone or tablet acts as the companion.
- Both devices are on a trusted private Wi-Fi connection, or can communicate through a separately configured authenticated private VPN.
- The Android Phone app remains the main device's default Phone app.
- For ordinary call controls, host permissions such as CALL_PHONE, ANSWER_PHONE_CALLS and READ_PHONE_STATE may be needed.
- For optional experimental audio, the user must explicitly approve Android Wireless Debugging and provide the Android-owned pairing code.
- On the companion, the microphone permission is needed only if the user starts the optional voice stream.

No third gateway device, extra SIM, phone-number porting, root or Shizuku is part of the intended setup.

## Main-phone setup

1. Open PhnLnk and choose the main-phone role.
2. Grant the Android call-control permissions that the host screen requests.
3. Start the host service and check that the local pairing screen appears.
4. Choose **Pair a device** to create the host's temporary six-digit PhnLnk code.
5. Keep the phone on a trusted private network and record its current private IP address.

If the main phone's IP changes, the companion can update the saved address without clearing its pairing.

## Companion setup

1. Open PhnLnk on the second Android device and choose companion mode.
2. Open the host QR on the companion, then tap **Connect phone**. The QR includes the TLS certificate pin. For manual IP/code entry, compare all fingerprint groups with the main phone before confirming; no pairing data is sent before verification.
3. Wait for a successful pairing. The device saves a bearer token privately.
4. Enable incoming-call alerts if desired and allow Android notifications.
5. Use the dialer, Contacts, Favorites and Recents tabs. The contacts are read from the companion itself.
6. For calls, expect the main phone's actual SIM to place or answer the call.

**Do not confuse the two six-digit codes:** PhnLnk device pairing connects the two app instances. Android Wireless Debugging pairing separately grants the host permission to start a local shell helper.

## Optional guided audio setup on the main phone

1. From the host screen choose **Guided audio setup**.
2. Tap **Start guided setup** and allow its notification, if requested.
3. Android opens Developer options. Enable Wireless Debugging yourself.
4. Select Android's **Pair device with pairing code** option.
5. Keep the Android pairing dialog open and enter its six-digit code in PhnLnk's notification.
6. Once Android approves pairing, the app saves its identity and can automatically retry the helper while debugging is available.
7. The helper will report ready when its shell process answers. This does not prove caller audio capture or injection.

If automatic ADB discovery fails, Android's **Wireless Debugging main screen** shows the connection address and port. The user may enter the connection port in the app. It is not the temporary pairing popup's port.

The app cannot silently turn Android debugging on after Android disables it. Re-entering the original pairing code is not normally necessary while the identity remains authorized.

## Experimental live audio workflow

A successful media protocol connection requires a real active cellular call, a running V3 shell helper, compatible Android audio backends and an authorized companion.

1. On the main phone, with a consenting other participant, start a real cellular call using the normal Phone app or companion call controls.
2. Keep Guided audio setup enabled and the helper connected. Review the selected capture and uplink methods.
3. On the companion, tap **Start voice on this device** and grant microphone access.
4. The app opens a voice foreground service and a separate encrypted socket to the host.
5. The host tries its current Android capture and injection routes and reports READY or a specific error.
6. If the connection opens, the companion microphone and speaker exchange PCM frames with the host.
7. Use the on-screen microphone mute and speaker/earpiece controls.
8. End the voice session or cellular call to stop the experimental audio path.

A READY indication is not a successful call test. If actual speech is absent, do not rely on the app for ordinary or emergency calls. Use the stock Phone app.

## Networks and away-from-home operation

The prototype does not provide a public internet relay or configure a VPN. The voice port uses AES-GCM; pairing and control use pinned HTTPS. Both apps must be updated, and an existing Android companion verifies the host once while retaining its saved token. For an away-from-home private connection, the user would need to provision a secure VPN on both devices using an independent service and verify routing.

Do not port forward TCP 47321, 47322 or the Android debugging port to the internet.

## Disconnect and revoke

- Companion: use **Disconnect this device** to clear the saved host address and bearer token, and stop the voice service.
- Host: revoke paired PhnLnk devices to invalidate host token hashes and close active media sockets.
- Host audio: choose **Turn off automatic audio access** to stop helper reconnect attempts. This does not necessarily remove Android's saved ADB pairing.
- Android: open Developer options > Wireless Debugging > Paired devices to revoke Android debugging identities.

Uninstalling removes private app storage and may remove saved PhnLnk pairings. When updating a permanently signed release, install on top with a higher versionCode rather than uninstalling.

## Emergency calls

PhnLnk is not tested, certified, or supported as an emergency dialer. Keep the original Phone app available for emergency calling. Remote control and voice tunneling can fail when the network, service or helper is unavailable.

Existing saved Android pairings are preserved during the HTTPS upgrade. Open the companion to compare the main phone's Security fingerprint once. An old app speaking HTTP cannot connect to the upgraded TLS listener. There is no fallback to plaintext. See [Control security](CONTROL_SECURITY.md).

For reliability checks and unresolved compatibility, see [Test plan](TEST_PLAN.md) and [Troubleshooting](TROUBLESHOOTING.md).
