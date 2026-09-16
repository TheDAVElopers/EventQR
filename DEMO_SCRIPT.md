# EventQR — Live Demo Workflow Script

A narrated, chronological demo covering the three public-facing roles: **Organizer**, **Staff**, and **Attendee**. The demo follows one story: the Tech Conference 2026. The Organizer builds the event, the Attendee registers and earns a QR credential, Staff validate that credential at the door, and the Organizer closes the loop with live stats and ID printing.

Total runtime target: ~15–20 minutes.

## Demo Story & Roles

| Role | Account (recommended) | What the audience sees |
|---|---|---|
| Organizer | `organizer1@eventqr.com` / `Organizer@123` | Owns the event: configures it, assigns staff, sets scan purposes & rewards, sees dashboards/reports |
| Staff | `staff1@eventqr.com` / `Staff@123` | At the door: scans attendee QR codes, performs check-ins/exits, view scan logs |
| Attendee | `attendee1@eventqr.com` / `Attendee@123` | Discovers the event, registers, receives QR credential + email, redeems a reward |
| Admin (supporting) | `admin1@eventqr.com` / `Admin@12345` | Creates the Organizer and Staff accounts (setup only, off-camera if you prefer) |

Super Admin exists for platform-level setup: `superadmin@eventqr.com` / `SuperAdmin@123`.

## Prerequisites

- Backend running (Spring Boot, port `10000`) with PostgreSQL up — see `README.md` "Local Development Setup".
- Android app installed on at least **two devices/emulators** (ideal: one "Attendee" device, one "Staff" device). The Organizer can present on either.
- Mobile `ApiConfig.kt` `BASE_URL` points at the running backend (Render URL by default; set `EVENTQR_BASE_URL`/edit `ApiConfig` for local).
- Accounts above exist. On a fresh DB: log in as Super Admin → create Admin (`POST /api/v1/users`), then Admin creates Organizer and Staff.
- QR email (Brevo) works if the attendee email is real; the in-app QR credential is the fallback and should be shown regardless.

## Pre-Demo Checklist

1. Staff account is created and **not yet assigned** to the event (assigning is a demo moment for the Organizer).
2. Decide the event capacity and the two scan purposes you'll show (check-in + one more, e.g. exit or a custom "Booth Visit").
3. Create one reward ahead of time *or* create it live during the Organizer section — live is more impressive.
4. Confirm the attendee will have a reachable email (to show the "QR sent by email" beat).
5. Silence other devices; stage 🎬.

---

## ACT 1 — The Organizer Builds the Event

> Narrator line: *"Everything starts with the Organizer — who controls the event, its staff, its rules, and its data. In EventQR the Organizer's phone is a command center, not a ticket booth."*

### 1.1 Sign in as Organizer
1. Open the app, log in with `organizer1@eventqr.com`.
2. Land on the **Organizer Dashboard** — role guard means only organizers see this. Mention the role-specific home screen, the notification bell with unread badges, and the swipe-to-refresh / skeleton loading.

### 1.2 Create the event
1. From the dashboard choose **Manage Events → Create Event**.
2. Fill the event (name **Tech Conference 2026**, dates, location, capacity).
3. Publish/open it for registration.
   - *Optional alternate path:* if you want to show the self-serve flow, an attendee can **Request an Event**, the Admin approves it, an `EVENT_REQUEST_APPROVED` notification fires, and the requester can be **upgraded to Organizer** on the spot — a great "anyone can become a host" beat. Use this instead of admin-created organizer if the demo runs long.

### 1.3 Assign staff
1. Open the event details → **Staff assignment**.
2. Add `staff1@eventqr.com` to the event **now, live**, and call it out: *"Before the staff member was assigned, they had nothing to scan — assignment is what activates their scanner for this event."*

### 1.4 Configure scan purposes (the "what is being scanned" rules)
1. Open **Scan Purposes** for the event. Show the seeded defaults (e.g. verification / `REGISTRATION_LOOKUP`, `ID_PRINT`).
2. Create (or point at) a **Check-In** purpose with a points rule (e.g. +10 points per check-in) — records for this purpose update attendee point balances.
3. Add one flavor purpose — e.g. **Booth Visit** — with its own points and a `max_uses_per_registration` rule.

### 1.5 Reward setup
1. Open **Rewards**, create a reward (e.g. "Tech Conference Merch" = 50 points).
2. Connect it to the event. Note it flows through: scans → points → redemption.

### 1.6 Preview the ID template (organizer differentiator)
1. Open the **ID card template/preview** shared with the printing pipeline.
2. Narrate: *"The same layout the organizer previews here is what gets rasterized into a printable card with the attendee's QR."*

✅ **Act 1 checkpoint — audience takeaway:** *The Organizer defines every rule of the event before anyone walks in.*

---

## ACT 2 — The Attendee Journey

> Narrator line: *"Now the other side of the platform — the attendee. Zero training, zero special accounts: they just sign up and register."*

### 2.1 Register & sign in
1. On the Attendee device: register a fresh account (or log in as `attendee1@eventqr.com`).
2. Attendee home opens with the 5-tab shell: **Home / Events / Registered / Rewards / Profile**.

### 2.2 Find the event
1. Open the **Events** tab → find **Tech Conference 2026** (published by the organizer in Act 1).
2. Open the event detail — dates, location, capacity, registration button.

### 2.3 Register + receive QR credential
1. Tap **Register**. 
2. The system issues the attendee's QR credential (payload format `EVQR-<32 hex>`).
3. **Show the credential screen** and the email: the backend sends the QR **asynchronously via Brevo** (mention the `QREmailService` queue — the app keeps working while the mail goes out).
   - Flip to the attendee's inbox and show the emailed QR: *"The credential is not trapped in the app — it travels with the attendee."*

### 2.4 Registered tab
1. Open the **Registered** tab → show the event card and registration status.
2. Optional: cancel & re-register to show the lifecycle, then re-register to keep the demo data clean.

### 2.5 (Optional) Profile
1. Show attendee Profile: their data, registration state, session-aware role switching.

✅ **Act 2 checkpoint — audience takeaway:** *Registration is a dead-simple mobile flow that ends with a portable QR credential.*

---

## ACT 3 — Staff at the Venue (QR Check-In)

> Narrator line: *"This is the moment every attendee door-passes through. The Staff app turns the phone into the gate — and it's the same phone as everything else."*

### 3.1 Sign in as Staff
1. On the Staff device: log in with `staff1@eventqr.com`.
2. Staff lands on the 4-tab shell: **Dashboard / Scan / Events / Logs**. Note the role guard and the **portal-switcher chip** in the corner (they can switch portals without logging out).

### 3.2 Assigned events
1. Open the **Events** tab — only the event(s) the Organizer assigned them appear (that assignment from Act 1 paid off).
2. Open **Tech Conference 2026** — the Scanner now carries that `eventId`.

### 3.3 Cash out with a scan (the star beat)
1. Attendee device: open the QR credential full-screen.
2. Staff: **Scan tab → scan the QR** → check-in transaction recorded (check-in purpose, +points rule applied).
   - Show the success feedback on the Staff screen.
3. Do it again → the rule engine blocks a duplicate approval (call out the partial unique guard: one approved scan per registration+purpose) — *"security guardrails built into the rules, not the human."*

### 3.4 More scan purposes
1. Run the attendee **Exit** (or **Booth Visit**) scan → transaction logged with the second purpose.
2. Flip to the attendee device: point balance updated (**Rewards** tab). If the attendee crossed the reward threshold, they can claim it — and staff can scan the **redemption** purpose, moving it to `REDEEMED`.

### 3.5 Logs & dashboard (staff view)
1. Open **Logs** → transaction history with purpose, timestamp, attendee, points.
2. Open the Staff **Dashboard** → "Scans Today" and "Check-ins Today" tiles → these deep-link straight into the Scanner and event lists.

### 3.6 Show a denial (optional, great for cameras)
1. Have the staff scan a stub/invalid QR → rejected scan with a clear error; nothing is recorded.

✅ **Act 3 checkpoint — audience takeaway:** *Staff verify credentials in seconds; each scan is a governed, logged, point-earning transaction — with a staff being actively assigned as the gate.*

---

## ACT 4 — Organizer Close-Out (Verification, Data, Printing)

> Narrator line: *"The event is over. This is where the Organizer's event becomes evidence."*

1. Back on the Organizer device: dashboard refresh → registration vs. check-in counts, points ledger, reward redemptions, notification bell now lit.
2. Open **Reports** / transaction logs for the event → show the check-ins recorded by Staff, filtered by purpose.
3. **ID card printing**: open a registered attendee and print their QR ID card — the same `AndroidIdPrinter` pipeline that rasterizes the QR + card fields into a printable CR80 card (batch 3×3 for a sheet, or single reprint).
4. **Audit trail** (admin/super admin beat, if time): an Admin views the audit logs and confirms each action — account creation, event creation, scans — is recorded.

✅ **Act 4 checkpoint — finale takeaway:** *One platform: the Organizer rules, the Staff verifies, the Attendee participates, and every action leaves a trace.*

---

## Suggested Scripted One-Liners

- **Organizer:** "Before anyone arrives, the organizer decides everything about the room — staff, scans, points, rewards — from one screen."
- **Attendee:** "No paper tickets, no queue to a booth. Your pass is your QR, and it lives in your phone *and* your inbox."
- **Staff:** "The same phone that lets me live my day becomes the door check. One scan, logged forever, points awarded."
- **Close:** "Register → Credential → Verify → Reward → Report. One QR, one platform."

## Cleanup / Reset Between Runs

- Soft reset: cancel the attendee registration and re-register; toggle the event closed and reopen.
- Hard reset: in an Admin/API session — `DELETE` the test event (cascades registrations/scan purposes/transaction rules), reseed the reward, re-create staff assignment.

## Demo Pitfalls to Avoid

- Staff scanning before the assignment is made → trips `requires_staff_assignment` rule (show it as a feature, not a bug, if it happens live).
- Duplicate same-purpose scans → blocked by the unique guard; always expect it and phrase it as the guardrail working.
- Attendee email shows no QR → fall back to the in-app credential screen and say "and the same QR is pushed to their inbox".