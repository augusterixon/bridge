#!/bin/bash

echo "🌉 Bridge Provisioner"
echo "Waiting for device..."
adb wait-for-device

echo "Opening app installs — tap Install on each one, then come back..."

APPS=(
    "com.whatsapp"
    "com.spotify.music"
    "com.bankid.bus"
    "com.ubercab"
    "com.grabtaxi.passenger"
    "com.microsoft.authenticator"
)

for pkg in "${APPS[@]}"; do
    echo "  → Opening $pkg"
    adb shell am start -a android.intent.action.VIEW -d "market://details?id=$pkg"
    sleep 3
done

echo ""
read -p "Press Enter once all apps are installed..."

echo "Installing Bridge..."
adb install -r ~/dev/bridge/app/release/app-arm64-v8a-release.apk

echo "Setting Device Owner..."
adb shell dpm set-device-owner com.bridge.device/.MyDeviceAdminReceiver

# Grant WRITE_SETTINGS so Bridge can control system brightness
# (cannot be granted via DevicePolicyManager, must be done via appops)
echo "Granting WRITE_SETTINGS..."
adb shell appops set com.bridge.device WRITE_SETTINGS allow

echo "✅ Done. Bridge is provisioned."