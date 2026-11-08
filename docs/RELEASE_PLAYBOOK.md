# Build and release playbook

This release procedure protects the user's saved pairings and the permanent Android signing identity. The presence of a compiled APK or successful GitHub Actions workflow is not proof of cellular audio compatibility.

## Repository and source layout

The repository is `richbeatty/PhnLnk`. Android app source lives under `app/`. The shell UID helper lives under `shellprobe/` and is built into an app asset. Maintainer invariants live at `tools/maintainer_check.py`. Pure JVM media protocol checks live at `tools/voice-tests/`. The Actions workflow is `.github/workflows/android.yml`.

The source of truth for product constraints is [MAINTAINER.md](../MAINTAINER.md). Feature claims must agree with [Known limitations](KNOWN_LIMITATIONS.md).

## Standard build sequence

1. Read the product contract, changelog, docs index and impacted components.
2. Make changes on a branch or coordinated commit. Do not mix unreviewed speculative audio features with a claimed release.
3. Run the source-level maintainer checks: `python3 tools/maintainer_check.py`.
4. Run the actual Java voice socket tests: `bash tools/test_voice_protocol.sh`.
5. Run `bash tools/test_control_tls.sh` and `python3 tools/test_audio_policy.py`. Ensure Java 17 and Android SDK API 35 / D8 are available.
6. Build the shell helper with `bash shellprobe/build.sh`, which copies the DEX into the app assets.
7. Compile debug with `gradle --no-daemon :app:assembleDebug`.
8. Inspect the APK and verify the helper is bundled at `assets/phnlnk-probe.jar`.
9. Compile release with `gradle --no-daemon :app:assembleRelease`.
10. Use the user's existing permanent release signing identity, not a new debug or random signing key.
11. Verify the signed APK, its package ID, versionCode, and signing certificate against the preceding permanently signed build.
12. Only after a successful CI run, documented results, and the user's permission should a release candidate be distributed.

The GitHub workflow automatically performs source checks, voice protocol checks, helper build, Android compilation and unsigned release artifact uploads. It does not claim device compatibility.

## Versioning

The Android app keeps applicationId `com.richbeatty.phnlnk`.

Bump both `versionCode` and `versionName` for every installable release. Update the exact version assertions in `tools/maintainer_check.py` and add a changelog entry. A new versionCode must be larger than every previously shipped signed APK.

Current code after this change targets version 0.9.3 with versionCode 17. The voice protocol still uses `PHN1`; Java semantic version changes do not imply a wire protocol change.

## Signing key rules

- Reuse only the permanently pinned PhnLnk signing keystore and certificate.
- Never commit a keystore, base64 keystore, private password, signing secrets, or device pairing identity to GitHub.
- Never print a private password to workflow logs.
- Never share the signing key as an ordinary user download.
- Verify certificate SHA-256 fingerprint against a known earlier signed release.
- Preserve private files only in approved local storage or GitHub encrypted Actions secrets controlled by the owner.
- If the signing material is unavailable, do not fabricate a replacement while claiming it can update an existing install.

An APK with a different signing certificate cannot normally upgrade the prior application in place. Uninstalling to change certificates also removes local app state and saved pairing.

## CI behavior

A normal push to `main` runs CI and uploads unsigned candidate and debug test artifacts. Neither is a production ready, permanently signed update.

An explicit `workflow_dispatch` run only publishes a permanently signed prerelease if the correct GitHub Actions secrets have been configured. Required names in the existing workflow:

- `PHNLNK_KEYSTORE_B64`: base64-encoded permanent PKCS12 keystore.
- `PHNLNK_STORE_PASSWORD`: private keystore password.

The release workflow is intended to reject missing credentials instead of publishing a debug-signed APK. The user has not been asked to post private credentials into the conversation or repository.

## Release notes

Every release note must contain:

- Application version and source commit.
- Android SDK and supported device range.
- Major implemented changes, specifying whether validated only in CI.
- Security and network behavior, including pinned HTTPS 47321, one-time certificate verification for saved pairings, and no plaintext fallback.
- Explicit audio status on the physical MP01.
- Whether the stock Android Phone app remains default.
- Known upgrade compatibility and signing identity result.
- Known limitations for away-from-home operation, cellular HAL and emergency calling.
- Any migration step and rollback options.

Never use "fully working audio" or "tested" simply because the shell helper responds, a packet decrypts, or Gradle succeeds.

## Rollback and failure handling

If a candidate fails CI or its signing identity does not match, do not distribute it. If a newer version has a functional regression, source-revert the code and release with a **higher** versionCode and the same certificate. Installing a prior lower-version APK may be blocked by Android. Do not advise uninstalling except with clear warning that pairing and saved configuration may be lost.

If a live MP01 test reports silence, keep the full-duplex claim blocked. Record exact backend, phase and level results. If there is a host-app crash, prioritize call isolation, resource cleanup and preserving the default Phone app.

## Manual acceptance beyond CI

CI cannot test two-way carrier audio on the owner's physical phone. Release as a documented preview only until [Test plan](TEST_PLAN.md) gates have direct real-call evidence, including both directions of understandable speech, end-of-call cleanup, incoming call handling, stable screen-off operation and privacy review.

Related: [Security](SECURITY.md), [Maintainer workflow](MAINTAINER_WORKFLOW.md), [Known limitations](KNOWN_LIMITATIONS.md).
