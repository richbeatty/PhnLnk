# Third-party licenses and distribution obligations

## Liblinphone / Linphone Android SDK

PhnLnk's experimental Bluetooth voice mode embeds `org.linphone.minimal:linphone-sdk-android:5.4.129`, downloaded from the official Linphone Maven repository.

The Linphone SDK is open source under GNU Affero General Public License version 3 (AGPLv3). The combined distributed application must comply with that license, including providing the complete corresponding source code to recipients and appropriate license notices.

- https://www.linphone.org/en/faq/
- https://www.linphone.org/en/liblinphone-voip-sdk/
- https://www.gnu.org/licenses/agpl-3.0.en.html
- https://gitlab.linphone.org/BC/public/linphone-sdk

PhnLnk's own original source files are released under the MIT License in the repository's LICENSE file. Where those files form part of a combined application linking the AGPL-licensed SDK, the distributed combined work is governed by the obligations of AGPLv3.

If you produce and distribute an APK built from this project, you must ensure the corresponding complete source, build scripts, dependencies, and licensing materials are offered in compliance with AGPLv3. Do not distribute the APK as MIT-only software.

## ZXing

The QR encoder uses `com.google.zxing:core:3.5.3`, Apache License version 2.0.

- https://github.com/zxing/zxing
- https://www.apache.org/licenses/LICENSE-2.0

## Other source projects

Research references such as Voice SIP Agent, GSM2SIP, CallScoot, and Asterisk's Bluetooth gateway are linked in the research document. Their source has not been copied into PhnLnk. They are not bundled into the Android APK.
