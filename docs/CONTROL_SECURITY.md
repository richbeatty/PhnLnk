# HTTPS identity, pairing and migration

## 0.9.5 AndroidKeyStore repair and release gate

Android Conscrypt delegates opaque EC TLS signatures through NONEwithECDSA after hashing the transcript. The original v1 host key authorized only SHA256, which does not authorize this raw operation. Version 0.9.5 creates new host keys under phnlnk_control_tls_v2 with DIGEST_NONE and SHA256. NONE here means signing an already-computed TLS digest; it does not disable TLS hashing, encryption or peer verification.

Existing v1 identities are not silently replaced. Listener startup detects missing raw-sign authorization and reports HOST_TLS_KEY_NEEDS_REPAIR. The host owner explicitly confirms Repair encrypted connection. Repair retains v1, creates/selects v2 and keeps all bearer tokens/hashes and separate ADB identity. The key manager serves only the selected alias. Each existing companion must explicitly compare and trust the new host fingerprint; old pins continue to fail closed until then. Repeated repair reuses v2 rather than rotating it. The APK signing identity is independent and unchanged.

CI now requires seven tests of real Android 14 KeyStore, Conscrypt, HttpBridge, HttpsURLConnection, legacy-key failure, explicit repair and pairing persistence before any signed publication. An emulator test is not MP01 hardware, Wireless Debugging or speech acceptance. See [reliability reset](RELIABILITY_RESET_2026-11-10.md). The v1 description below records the original 0.9.3 design.

Version 0.9.3 replaces plaintext HTTP on TCP 47321 with HTTPS on the **same port**. The JSON endpoints and saved bearer-token format remain unchanged. There is no plaintext fallback or redirect listener. Media remains the independent authenticated AES-GCM protocol on TCP 47322. This secures the implemented control transport; it does not establish MP01 audio or public-service readiness.

## Host identity

`HostTlsIdentity` creates an EC P-256 key and self-signed certificate in the host app's AndroidKeyStore under `phnlnk_control_tls_v1`. It is a per-install transport identity, **separate from the permanent APK release signing certificate**. Ordinary app updates retain it. App data reset, reinstall, device reset or keystore loss may change it and require explicit trust verification again. It is not silently regenerated on a verification failure.

TLS 1.2/1.3 are enabled. The host screen shows the complete SHA-256 certificate fingerprint, grouped for comparison. A new QR includes the private address, expiring pairing code and fingerprint. Pairing state and token hashes remain in their existing preferences.

## New companion pairing

The user opens the host's QR and explicitly taps **Connect phone** on the companion. The companion verifies the certificate against the QR fingerprint before sending the pairing code. Manual address/code setup first makes a TLS-only discovery connection, displays a candidate fingerprint and asks the user to compare **all groups** with the host screen. No HTTP, pairing code, bearer token, contacts or call command is sent during discovery. Discovery is unauthenticated until that physical comparison, so a user must cancel if it differs.

The verified pin is saved with the pairing token. Each HTTPS request uses its own pinned SSLContext and a verifier that checks the same certificate identity. A mismatch fails before an application request. System CA trust is not globally altered. Redirects are refused. The pin, rather than the changing private IP address, is the endpoint identity.

The six-digit code is not a PAKE or cryptographic replacement for certificate verification. Do not add automatic trust-on-first-use to manual setup. A screenshot of the QR carries a temporary pairing secret; do not publish it.

## Existing companion migration

The existing bearer token and host authorization hashes are retained. Before any legacy companion token can be sent, the companion asks the user to verify the host certificate once. On confirmation it changes the saved `http://` address to `https://` and saves the pin atomically. Background polling fails closed until verification. It does not fall back to plaintext, uninstall, clear preferences or demand another Android ADB pairing.

Both apps must be updated together. An older app using HTTP cannot speak to the HTTPS listener. Updating only the companion cannot make an older host support TLS. Changing the saved private address retains the pin and token, so moving from home Wi-Fi to a VPN address does not require a new pairing. If a device is replaced or its identity changes, compare the fingerprint on the intended host before using **Verify main phone security fingerprint**; do not dismiss a mismatch automatically.

Browser storage is origin-specific: moving from HTTP to HTTPS does not migrate an existing browser's saved token. A browser may require new PhnLnk pairing. Its normal CA validation also does not trust the self-signed host automatically. Verify the complete browser certificate fingerprint against the host before explicitly trusting it; do not bypass an unknown warning. The native pinned companion is the intended two-Android-device client.

## Availability limits

The control listener allows four active workers and eight queued sockets; excess connections close. Active requests have a 15-second absolute socket deadline as well as read timeouts. Closing the host service closes tracked sockets. The media listener permits four simultaneous authentication workers, a nine-second absolute handshake deadline and one authenticated active voice owner. An unauthenticated idle socket no longer reserves that voice owner. Busy authenticated companions receive an encrypted HOST_BUSY error.

These quotas limit resource use, but do not provide internet-scale abuse protection. Host pairing has its existing expiring code and failed-attempt limit. There is no public rate-limited rendezvous, account recovery, remote identity directory, audited public deployment or first-party push service. Keep ports private; no port forwarding.

## What is validated

`bash tools/test_control_tls.sh` compiles production `ControlTls.java`, generates two ephemeral test certificates and uses actual loopback TLS sockets. It checks strict pins, valid application traffic, changed identity rejection, payload-free discovery, session identity comparison and unchanged default CA trust. No release signing material is used.

The suite runs with both TLS 1.2 and TLS 1.3 clients. Certificate-rejection server cases allow either TLS alerts or TCP SocketException when the client closes during the handshake; client rejection is still mandatory and application bytes still fail the test. Positive traffic and discovery do not ignore socket errors. See [resumed engineering status](ENGINEERING_STATUS_2026-11-10.md).

Android compilation verifies integration types. The JVM tests do **not** execute AndroidKeyStore, on-device update persistence, QR scanning, browser trust UI or Android background services. Those remain device acceptance gates. Never describe JVM TLS tests as an on-device security audit.

For threat model and credential handling, see [Security](SECURITY.md). For away-from-home use, see [Phone Link and remote networks](PHONE_LINK_AND_REMOTE_NETWORKS.md).
