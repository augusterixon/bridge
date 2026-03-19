#!/usr/bin/env bash
set -e
PKG="com.bridge.device"
ACT=".MainActivity"

adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb shell am start -S -n "$PKG/$ACT"
