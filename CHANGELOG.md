# Changelog

## v0.7.0, interface refresh

- New reusable native visual style with muted green surfaces, readable typography, clear primary actions and consistent controls, on host, companion, and role selection.
- Companion reorganized into a focused keypad, contacts, favorites, and recent call-request views.
- Live call status and answer/end controls are now visibly separated from number entry.
- Saved pairing, incoming-call alerts, host Wi-Fi address changes, and disconnection are organized under Connection settings.
- Main phone has a simpler high-contrast status and QR pairing view.
- Corrected contact search rendering to keep the search field visible while refreshing results.
- Preserved non-default-dialer calling and the explicit limitation that voice audio remains on the host phone.
- No new remote cellular audio capability is claimed; Android default Phone role still does not grant modem audio capture.


## Unreleased, v0.6.1 maintenance changes

- Formalized permanent requirements in `MAINTAINER.md` and `AGENTS.md`.
- Kept the host's regular Phone app unchanged; removed requests to set PhnLnk as the default Phone app.
- Removed Wireless Debugging from the host's main setup screen. The v0.6 experimental code remains in Git history for reference but is not a normal supported calling route.
- Added automatic regression checks for app identity, device-neutral branding, unsupported login requirements, temporary signing, and release gates.
- Split ordinary debug build artifacts from signed GitHub Releases.
- Refused unsigned or temporary-debug-signed GitHub Releases, pending the owner's private Actions signing secrets.
- No claims of native cellular audio relay. No changes to the carrier, SIM or number.

## v0.6.0, experimental audio diagnostics

- Compiled embedded self-ADB shell audio diagnostics for compatible devices using optional Wireless Debugging.
- Diagnostic audio tests did not establish working remote voice.
- Distributed with runner-debug signing certificate, which prevents updating different-signature versions in place.

## v0.5.0, hardware-assisted experiment

- Experimental SIP and Bluetooth gateway approach. Requires a third device and does not meet the permanent product contract.

## v0.4.0

- Optional default-dialer-based DTMF and call controls. Default-dialer requirement rejected.

## v0.3.0

- Android host and companion roles, native keypad, local contacts, favorites and call status.

## v0.2.x

- QR and short-code pairing, remembered local browser keys.

## v0.1.x

- Initial authenticated local-network call-control app.
