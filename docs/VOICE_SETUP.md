# Optional Bluetooth voice bridge setup

This is the **only audio route in PhnLnk 0.5** that can actually carry a cellular conversation onto the secondary Android device. It is experimental until verified on your specific phone and Linux Bluetooth adapter.

## What you need

- Your main Android phone with its original cellular SIM and Bluetooth.
- A nearby, always-on **Linux computer with Bluetooth** (for example a mini PC). It must remain within Bluetooth range of the main phone.
- A secondary Android device with PhnLnk, Wi-Fi and a working microphone and speaker.
- A Bluetooth hands-free-to-SIP gateway running on Linux. One existing open implementation is [k-chatz/bluetooth-gsm-sip-gateway](https://github.com/k-chatz/bluetooth-gsm-sip-gateway), which uses Asterisk `chan_mobile`.
- A **trusted private network**. Keep SIP and RTP off the public internet.

## Important security warning

The upstream k-chatz demonstration uses a privileged Docker container with Ubuntu 18.04 (end-of-life), a sample short SIP password, and SIP/RTP without end-to-end encryption. It is useful for proving the Bluetooth audio route but should not be used as an unprotected public server. Replace all default passwords, restrict network access, and use a maintained Bluetooth/SIP stack for ongoing use.

Do not open UDP 5060 or RTP ports to the internet. The first tests should be on one private LAN. A production remote deployment needs a secure private tunnel or maintained TLS/SRTP infrastructure, NAT traversal and hardening.

## Prove the real two-way path first

1. Pair your **main Android phone** with the Linux Bluetooth adapter. Enable call audio for that Bluetooth connection on the phone.
2. Set up the Linux Bluetooth-to-SIP gateway. For the upstream reference, see its own README and verify Asterisk reports the mobile phone as connected.
3. Place a test call to the SIM number. Verify the Linux gateway detects the call.
4. Install a known SIP softphone on your secondary device or PC **temporarily for diagnosis**. Connect with the local SIP credentials and prove you can hear and speak to someone through an actual carrier call.
5. Once the gateway passes that test, use PhnLnk's integrated SIP mode instead of keeping a separate softphone.

## Set up PhnLnk's integrated voice mode

1. Install PhnLnk on your secondary Android device and choose **Connect to my other phone**. Pair the usual call-control connection.
2. Open **Set up voice calling** from the companion screen.
3. Enter the Linux SIP gateway's private IP, the SIP extension (e.g. `1001`), and a strong unique password of at least twelve characters.
4. Tap Save, then **Start voice bridge**. Allow microphone and notification permissions.
5. Wait until the status says **Voice bridge connected**. This requires a real SIP registration response, not simply being able to ping a server.
6. Dial from PhnLnk's normal contacts or keypad. When SIP is registered, PhnLnk routes the request through the Bluetooth-to-SIP gateway and opens its voice UI. Your microphone and speaker are used by the SIP library.
7. Incoming calls should ring through the gateway's SIP extension. Tap the PhnLnk voice notification, then **Answer here**. Test both directions.

During an active SIP voice call, keypad digits are sent as SIP DTMF. This depends on gateway RFC 4733/in-band interoperability.

## Current limitations

- This build cannot automatically configure the Linux gateway, Bluetooth pairing or firewall.
- The gateway and companion must reach each other on a private network. Local IP input is enforced for the prototype.
- It has not been tested end-to-end on the user's hardware.
- Incoming SIP notification delivery depends on a foreground Android service remaining enabled. If the system force-stops it, restart PhnLnk.
- At any moment the main cellular line can only sustain the call the gateway and Android modem support.
- If the Bluetooth-to-SIP gateway is off or unregistered, ordinary PhnLnk call control can still place a call, but **the secondary device won't have call audio**.
- Do not use the prototype for emergencies.

## Audio engine and license

PhnLnk 0.5 uses Liblinphone 5.4.129 for SIP signaling and microphone/speaker audio. The SDK is offered under AGPLv3. The combined distributed APK must follow AGPLv3 licensing requirements. See https://www.linphone.org/en/faq/ and https://www.gnu.org/licenses/agpl-3.0.en.html . PhnLnk's own original MIT-licensed code remains accessible in this public repository.

## References

- https://github.com/k-chatz/bluetooth-gsm-sip-gateway
- https://github.com/efekurucay/callscoot
- https://github.com/michaellee8/esp32-bluetooth-voice-agent
- https://wiki.linphone.org/xwiki/wiki/public/view/Lib/Getting%20started/Android/
- https://source.android.com/docs/core/permissions/app-streaming

See [VOICE_RESEARCH.md](VOICE_RESEARCH.md) for the technical comparison of alternate approaches.
