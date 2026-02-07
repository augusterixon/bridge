# Bridge – Project Context (Authoritative)

Bridge is a fully provisioned Android device operating in **Device Owner + Kiosk mode**.

This is NOT:
- A launcher
- A parental-control app
- A focus mode
- A configurable Android experience

This IS:
- A locked-down, non-escapable device
- Sold ready-to-use
- Factory reset is the only exit

## Non-Negotiables
- Bridge is always Device Owner
- Lock Task (kiosk) is always enforced
- No user app installation
- Only curated, pre-approved apps may exist under the hood
- Apps are enabled/disabled via Bridge UI, not installed/uninstalled

## Allowed App Categories
- Auth apps (BankID, Microsoft Authenticator, etc.)
- Payments
- Messaging (WhatsApp)
- Travel (Maps, Uber, Bolt, Grab)
- Media (Spotify)

These apps:
- Exist as normal Android apps
- Are launched intentionally
- Are not browseable or discoverable

## System Rules
- HOME is permanently bound to Bridge
- Status bar is disabled
- Keyguard is disabled
- Boot always returns to Bridge
- Back/Home/Recents must never escape Bridge

## Design Philosophy
- Minimal
- Utilitarian
- No icons for decoration
- No playful UI
- No “settings for the sake of settings”

If there is ever a choice:
- Simpler > flexible
- Safer > powerful
- Explicit > clever

Assume this device may be used by:
- Professionals
- People recovering focus
- People who do not want configuration

Do not suggest:
- Allowing user-installed apps
- Escape hatches
- Debug shortcuts
- Developer toggles in production

## General

Key constraints:
- Bridge is Device Owner (set via adb dpm set-device-owner)
- Lock Task (kiosk) mode is enforced
- No app installation by users
- Users can only enable/disable curated existing apps
- Bridge is the HOME launcher
- Bridge must auto-start on boot
- Escape paths are intentionally removed

Important files:
- MainActivity.kt → kiosk enforcement
- MyDeviceAdminReceiver.kt → device owner + HOME enforcement
- BootReceiver.kt → auto-start after boot
- AndroidManifest.xml → HOME + admin + boot wiring
