# Bridge v1 Spec (Frozen)

**Owner:** August  
**Date:** 2026-01-28  
**Status:** Frozen for v1 (changes go into DECISIONS.md)

## 0. One-line definition
Bridge is **an iPod for 2026**: a tool that removes the need for a smartphone by providing only modern-life essentials.

## 1. Non-goals (explicitly NOT in v1)
- Web browsing (no general-purpose browser)
- Social media apps
- App store browsing / app discovery
- Email client
- Games
- Streaming content (Spotify/YouTube/Netflix)
- Photos/camera use (even if hardware exists)
- Personalization (themes, rearranging, widgets)
- Multiple profiles/modes

## 2. Target user and context
**User:** Someone who wants to stop using a smartphone but cannot due to modern infrastructure.  
**Context:** Travels with a computer; uses Bridge for essentials and USB-loads offline content.

## 3. Hardware target
**Supported device (v1):** Pixel [7 / 6a / 7a] only  
**Storage:** 128 GB minimum  
**Condition:** Refurbished allowed  
**Notes:** One model only for v1 to reduce QA + support.

## 4. Core principles (must hold)
1. **Tool, not phone**: boring, silent, finite.
2. **Zero friction for allowed tasks**, maximum friction for everything else.
3. **No discovery loops**: no feeds, recommendations, infinite scroll.
4. **Offline-first for content**: media/docs are loaded intentionally via USB.
5. **Clear exit**: user can revert to normal without shipping device back.

## 5. Home screen (Bridge Launcher) — exact menu
Order is fixed, text-only list:

1. Library
2. Phone
3. Messages
4. Maps
5. QR
6. Hotspot
7. Auth (BankID / 2FA)
8. Settings
9. Exit Bridge

## 6. Feature requirements (v1)

### 6.1 Library (USB-loaded files)
**Folders (fixed):**
- /Bridge/Music
- /Bridge/Documents
- /Bridge/Video (optional; include only if we decide yes)

**Library UI:**
- Music: list → play local files (offline only)
- Documents: list → open PDFs (and basic docs if easy)
- Video: list → play local files (offline only, no thumbnails grid)

**Rules:**
- No streaming, no recommendations
- No “recently added” feed
- No cloud sync
- Optional: search (default: NO in v1)

### 6.2 Phone
- Normal calls work
- No call recording features
- No suggested contacts UI beyond basics

### 6.3 Messages
- SMS supported
- Optional: WhatsApp allowed? [YES/NO]
  - If YES: WhatsApp is allowed but no other messengers.

### 6.4 Maps (online navigation only — no discovery)
Maps are treated as **infrastructure**, not exploration.

**Allowed:**
- Online search for destination/address
- Route planning and navigation
- Turn-by-turn directions
- ETA and rerouting

**Explicitly disallowed / avoided:**
- Restaurant reviews, star ratings
- Photos of places
- "Nearby" / "Explore" / recommendations
- Saved places and discovery feeds

**UI rule:** Entry point is always **Destination → Route**, not map browsing.

### 6.5 QR (camera as sensor only)
- QR scanning is supported and requires camera access.
- **No Camera app exists** on Bridge.
- Camera hardware is used **only** inside the QR scanner screen.

**Supported outcomes:**
- Map links → open route
- Plain text → display
- Ticket/doc links → show instruction ("Add via USB to Library") or open limited view if trivial

**Rules:**
- No photo saving
- No gallery
- No general-purpose browsing

### 6.6 Hotspot
- One-tap hotspot toggle
- Clear on/off state

### 6.7 Auth
- BankID supported (Sweden)
- Optional: Authenticator app supported? [YES/NO]
- Installation is controlled (no app store browsing)

### 6.8 Payments
Decision for v1: [A] Card-only (no NFC) / [B] NFC tap-to-pay allowed but hidden and constrained
Chosen: [A/B]
Notes:
- If NFC enabled, must not expose Wallet browsing/history/notifications.

### 6.9 Settings
Settings inside Bridge only includes:
- Hotspot toggle (if not separate)
- Brightness (optional)
- Sound profile (calls/messages only)
- Time/date (optional)
No other customization.

### 6.10 Exit Bridge
- Clear warning screen
- Provides step-by-step revert path
- Must enable factory reset / restore to normal condition without sending back device

### 6.11 Case (mandatory physical layer)

**The case is not an accessory. It is required for Bridge.**

**Purpose:**
- Remove “phone-ness” visually and physically
- Break smartphone muscle memory
- Add intentional friction to removal

**Design requirements:**
- Thickened profile (+4–6 mm minimum)
- Flat back (camera bump eliminated)
- Squared, tool-like edges (not rounded consumer styling)
- Matte industrial finish (non-gloss)

**Retention:**
- Snug fit + intentional removal friction
- Removal requires a tool step (e.g., recessed screw or pin latch)
- Must remain escapable without destruction

**Camera:**
- No visible camera cutout aesthetic
- Only minimal aperture required for QR scanning

**Color policy (v1):**
- Single color only: Matte Black / Graphite

**Rule:**
- Bridge ships with case installed. No “software-only” units.

## 7. Security + management constraints
- Device boots into Bridge launcher
- App installs blocked or allowlisted
- Play Store not accessible for browsing
- Notifications minimized
- No ability to add “just one more app” casually

## 8. Definition of Done (testable product)
A build is “testable” when:
- Bridge launcher is default and stable
- Library works via USB transfer
- Maps routes-only usable (online)
- QR scanning works for common cases
- Hotspot works
- Auth works (BankID setup is possible)
- Exit path works (restore to normal without shipping back)
- 5 days dogfooding completed with no showstoppers

## 9. Open questions (allowed, but must be resolved by Week 2)
- Final Pixel model: [ ]
- WhatsApp allowed? [ ]
- Payments strategy: [ ]
- Video in Library? [ ]

