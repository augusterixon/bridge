#!/bin/bash

echo "🌉 Bridge Provisioner"
echo "Waiting for device..."
adb wait-for-device

echo "Device connected. Installing Bridge..."
adb install -r ~/dev/bridge/app/release/app-arm64-v8a-release.apk

echo "Setting Device Owner..."
adb shell dpm set-device-owner com.bridge.device/.MyDeviceAdminReceiver

echo "✅ Bridge provisioned successfully."
