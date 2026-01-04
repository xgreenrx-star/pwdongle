#!/usr/bin/env bash
# Launch Android Emulator for PWDongle testing
set -euo pipefail

export ANDROID_SDK_ROOT="/home/Commodore/Android/Sdk"
export PATH="$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/platform-tools:$PATH"

echo "Starting PWDongle Android Emulator..."
echo "This will open a new window. Leave this terminal open."
echo ""

emulator -avd PWDongle_Emulator -no-snapshot-load &

echo ""
echo "Emulator starting in background (PID: $!)"
echo "Wait ~60 seconds for boot, then install APK with:"
echo "  adb install -r app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "To stop emulator: adb emu kill"
