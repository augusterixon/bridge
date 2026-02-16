# Bridge Decisions Log

This file records decisions that affect Bridge scope, philosophy, or constraints.

The v1 spec is frozen. Changes must be logged here.

---

## Template

### YYYY-MM-DD — Decision title
**Decision:**  
**Reason:**  
**Alternatives considered:**  
**Impact:**  

---

## Decisions

### 2026-01-28 — Maps are online but discovery is disabled
**Decision:** Maps may use online routing/search, but reviews/photos/explore surfaces are disallowed.  
**Reason:** Navigation is infrastructure, not entertainment.  
**Impact:** No offline-first requirement for Maps.

### 2026-01-28 — Camera hardware allowed only for QR scanning
**Decision:** No Camera app exists; camera is only used inside QR scanner.  
**Reason:** QR is essential infrastructure, photography is not.  
**Impact:** Case aperture must support QR only.

## 2026-02-12 — App distribution
Chosen: Android Enterprise + Managed Google Play (free) over Esper ($/device/month).

Reason:
- scalable, no per-device cost
- silent install & updates
- no consumer Google accounts
- decrease setup time per device

Tradeoffs:
- moderate initial setup complexity

## 2026-02-12 — Email host
Chosen: Zoho Mail Lite plan $15 per year incl tax, for business email. 