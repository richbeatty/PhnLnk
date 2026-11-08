# Changelog

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
