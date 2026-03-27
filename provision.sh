#!/bin/bash
set -euo pipefail

echo "🌉 Bridge Provisioner"
echo "Waiting for device..."
adb wait-for-device

echo "Installing Bridge..."
adb install -r ~/dev/bridge/app/build/outputs/apk/release/app-arm64-v8a-release.apk

echo "Setting Device Owner..."
adb shell dpm set-device-owner com.bridge.device/.MyDeviceAdminReceiver

if ! adb shell dpm list-owners | grep -q "com.bridge.device"; then
    echo "❌ Device Owner setup failed. Check for existing accounts."
    echo "   Run: adb shell dumpsys account"
    exit 1
fi
echo "✅ Device Owner confirmed."

# Grant WRITE_SETTINGS so Bridge can control system brightness
# (cannot be granted via DevicePolicyManager, must be done via appops)
echo "Granting WRITE_SETTINGS..."
adb shell appops set com.bridge.device WRITE_SETTINGS allow

echo ""
echo "✅ Bridge is Device Owner."
echo ""
echo "Next steps on the phone:"
echo "1. Bridge will launch automatically"
echo "2. Sign into Google account via Bridge settings if needed"
echo "3. Install required apps via the provisioner's account"
echo ""