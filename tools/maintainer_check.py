#!/usr/bin/env python3
"""PhnLnk source-level maintainer invariants.

Run before every commit and in GitHub Actions. These checks catch known
regressions; they do not substitute for device testing or a signed APK.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
failures = []


def content(path):
    p = ROOT / path
    if not p.is_file():
        failures.append(f"Missing required file: {path}")
        return ""
    return p.read_text(encoding="utf-8")


def require(ok, message):
    if not ok:
        failures.append(message)


agents = content("AGENTS.md")
maintainer = content("MAINTAINER.md")
changelog = content("CHANGELOG.md")
gradle = content("app/build.gradle")
host = content("app/src/main/java/com/richbeatty/phnlnk/HostActivity.java")
client = content("app/src/main/java/com/richbeatty/phnlnk/ClientActivity.java")
core = content("app/src/main/java/com/richbeatty/phnlnk/CallController.java")
pairing = content("app/src/main/java/com/richbeatty/phnlnk/PairingManager.java")
manifest = content("app/src/main/AndroidManifest.xml")
workflow = content(".github/workflows/android.yml")
readme = content("README.md")

require(len(agents) > 500, "AGENTS.md must remain meaningful and present")
require(len(maintainer) > 1000, "MAINTAINER.md must remain the living product contract")
require(len(changelog) > 100, "CHANGELOG.md must be maintained")
require("No root" in maintainer or "no root" in maintainer.lower(),
        "Maintainer must state no-root requirement")
require("Wireless Debugging" in maintainer and "default Phone" in maintainer,
        "Maintainer must record the user's no-debugging and no-default-phone requirements")

# Preserve the package identity and monotonically advancing release version.
require(re.search(r"applicationId\s+['\"]com\.richbeatty\.phnlnk['\"]", gradle),
        "The applicationId must remain com.richbeatty.phnlnk")
match = re.search(r"versionCode\s+(\d+)", gradle)
require(match is not None and int(match.group(1)) >= 13,
        "The versionCode must be at least 13 and increase on subsequent releases")
require("versionName '0.8.2'" in gradle,
        "Update this check alongside the changelog and new versionName")

# The user allows explicitly opted-in guided Android pairing, never an automatic privilege grant or default Phone role.
host_blocklist = [
    "ROLE_DIALER",
    "Enable in-call keypad",
]
for banned in host_blocklist:
    require(banned not in host,
            f"The normal host setup must never prompt for: {banned}")

require('secondary("Guided audio setup"' in host and
        'startActivity(new Intent(this,HostAudioActivity.class))' in host,
        "Optional guided audio entry point must be user initiated")
require('AudioAccessState.autoReconnect(this)' in host,
        "Host screen should restore explicitly enabled audio helper after reopening")
audio_ui = content("app/src/main/java/com/richbeatty/phnlnk/HostAudioActivity.java")
audio_state = content("app/src/main/java/com/richbeatty/phnlnk/AudioAccessState.java")
audio_service = content("app/src/main/java/com/richbeatty/phnlnk/AudioAccessService.java")
audio_pairing = content("app/src/main/java/com/richbeatty/phnlnk/AudioPairService.java")
require("requestPermissions(" in audio_ui and "POST_NOTIFICATIONS" in audio_ui,
        "Guided pairing must explicitly request notification permission")
require("ACTION_APPLICATION_DEVELOPMENT_SETTINGS" in audio_ui,
        "Only the user can enable Wireless Debugging in Android Settings")
require('Pair device with pairing code' in audio_ui,
        "Explain Android's explicit pairing approval")
require('auto_reconnect' in audio_state,
        "Remember the user's automatic reconnect setting")
require('AudioAccessState.isPaired(this)' in audio_service,
        "Never try privileged helper startup before the user has paired Android")
host_adb = content("app/src/main/java/com/richbeatty/phnlnk/HostAdb.java")
require("NetworkInterface.getByInetAddress(host)" not in host_adb,
        "Do not silently ignore Android's own mDNS advertisement due to interface address mismatch")
require("AudioAccessState.manualPort(context)" in host_adb and
        "No Wireless Debugging connection port found" in host_adb,
        "Keep the explicit connection port fallback and understandable error")
require("TimeoutException" in host_adb and "future.get(14000" in host_adb,
        "ADB commands must have a bounded wait, never stick on Connecting forever")
require("setManualPort(this,port)" in audio_ui,
        "Keep an optional manual connection-port recovery control")
require('openStream("exec:sh")' in host_adb,
        "Use a persistent exec:sh session, not repeated short-lived ADB shells")
require('openStream("shell:' not in host_adb,
        "Avoid libadb 3.1.1 short-lived shell stream CLSE handling regression")
require("Reconnecting once" in host_adb and "isClosedStream(error)" in host_adb,
        "Keep bounded recovery from transient ADB stream close")
require("future.get(14000" in host_adb,
        "Preserve hard timeout for stalled debugging commands")
require("Connection stalled. Tap Retry." in audio_state,
        "Stale Connecting state must be diagnosed, not displayed forever")
require("AudioAccessState.enable(this)" in audio_pairing,
        "Remember the approved pairing and reconnect choice")
require('status.put("audioRelayed", false)' in core,
        "Do not claim finished two-way audio after a shell probe passes")
require("setTurnScreenOn" not in host and "FLAG_SHOW_WHEN_LOCKED" not in host,
        "Host app must not deliberately wake the screen")

# Preserve proven basic call control, persistent pairing, and contact search.
require("telecom.placeCall(" in core,
        "Preserve ordinary CALL_PHONE outgoing calls without default role")
require("telecom.acceptRingingCall()" in core and "telecom.endCall()" in core,
        "Preserve supported answer/end call actions")
require('status.put("audioRelayed", false)' in core,
        "Never claim real audio relay without end-to-end device evidence")
require("getStringSet(" in pairing and "exchange" not in pairing,
        "Keep persisted per-device pairings (inspect any API migration)")
require("ContactsContract.CommonDataKinds.Phone" in client,
        "Preserve companion contacts search")
require('android:name=".MainActivity"' in manifest,
        "Maintain the Android app entry point in the manifest")
require('android:required="false"' in manifest,
        "Allow Wi-Fi-only Android companion tablets")

# Any GitHub Release must be permanently signed, never a disposable debug APK.
require("PHNLNK_KEYSTORE_B64" in workflow and "PHNLNK_STORE_PASSWORD" in workflow,
        "Release workflow needs BOTH permanent signing secrets")
require("assembleRelease" in workflow and "assembleDebug" in workflow,
        "Distinguish signed release builds from CI debug builds")
require("apksigner" in workflow and "verify" in workflow,
        "Verify release APK signature before publishing")
require("github.event_name == 'workflow_dispatch'" in workflow,
        "Releases must be explicitly triggered, not automatically published as debug")
require("softprops/action-gh-release" in workflow,
        "Signed APK release publishing workflow missing")
release_index = workflow.find("softprops/action-gh-release")
require(release_index >= 0 and "if:" in workflow[max(0,release_index-260):release_index],
        "GitHub release action must be guarded by an explicit condition")
require("if [ -z \"$KEY_BASE64\" ]" in workflow,
        "Missing signing secrets must FAIL, never silently fall back to debug")
require("app-release.apk" in workflow,
        "Publish the real signed release APK, not the ephemeral debug APK")

require("does not" in readme.lower() and "audio" in readme.lower(),
        "README must clearly disclose unavailable remote cellular audio")

if failures:
    print("PhnLnk maintainer checks FAILED:")
    for message in failures:
        print("  ERROR:", message)
    sys.exit(1)
print("PhnLnk maintainer checks PASSED. Device audio still requires real hardware testing.")
