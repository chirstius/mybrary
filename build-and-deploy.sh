#!/usr/bin/env bash
# Build, copy to Drive, and install on running emulator/device.
set -e

ADB="/c/Users/Chuck/AppData/Local/Android/Sdk/platform-tools/adb.exe"
APK="app/build/outputs/apk/debug/app-debug.apk"
DRIVE_DEST="G:/My Drive/Personal/Projects/Mybrary/mybrary-debug.apk"

echo "==> Building..."
./gradlew.bat --no-configuration-cache assembleDebug

echo "==> Copying to Drive..."
cp "$APK" "$DRIVE_DEST"
echo "    Copied to Drive."

echo "==> Installing on device..."
DEVICES=$("$ADB" devices | grep -v "^List" | grep "device$" | awk '{print $1}')
if [ -z "$DEVICES" ]; then
    echo "    No device/emulator connected — skipping install."
else
    for DEVICE in $DEVICES; do
        echo "    Installing on $DEVICE..."
        "$ADB" -s "$DEVICE" install -r "$APK"
    done
    echo "    Done."
fi
