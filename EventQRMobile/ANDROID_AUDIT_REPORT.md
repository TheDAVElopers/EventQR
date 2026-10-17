# EventQR Android Audit Report

Date: 2026-10-15/16. Baseline: `main` @ `f160921c`. Fixes are uncommitted in the working tree (not pushed).
Evidence folder (git-ignored): `MyFiles/audit/` — `results.md` (full test log), `static_audit.md`, `security_summary.md`, `crash_N1_rewards.txt`, `shots/*.png`.

## A. Executive Summary

- **Builds and launches:** yes. Baseline debug build, 498/498 unit tests and lint (0 errors) passed before any change.
- **Main workflows:** registration, login/logout, event browse/register/cancel, QR display/download, staff verification and logs, organizer event/purpose/rule/reward management, reports + CSV export, admin request review, account search and audit logs all work against the live backend, verified against the database.
- **Device baseline:** 83 checks: **63 PASS, 10 FAIL, 3 NEEDS REVIEW, 5 NOT RUN, 2 N/A**.
- **After fixes:** every in-app FAIL that was fixed re-tested **PASS** on the device (11 regression checks). Unit tests 498 → 555 (all pass), lint 0 errors.
- **Round 2 (2026-10-16/17):** the items first marked NOT RUN were run: release (R8) build, event-day flows on a live ACTIVE event (check-in, points, redemption, exit, ID print), password reset and change, admin approve/upgrade/enable/disable/delete. 22 more checks: 20 PASS, 2 FAIL (both ID-print issues, fixed in the app). A new on-device UI test suite passes 11/11 on the Xiaomi.
- **Backend:** the three backend findings (staff points/redeem authorization, guessable QR values, phone validation) are fixed in code with 867 passing backend tests, but **not deployed or migrated**. H1 (token self-renewal) is still open by decision.
- **Most important defects (all fixed in the app):**
  - Crash in organizer Rewards on early pull-to-refresh (High).
  - Transaction Rules could overwrite real rules with defaults after a failed load (High, code path).
  - Event registration counts stale after register/cancel (Medium).
  - Staff scan result Back stacked duplicate scanners (Medium).
  - Request Event accepted a contact number of "12" (Medium).
  - Edit Profile reported success for a blank name (Medium).
  - Organizer screens showed "no events" when the event list failed to load (Medium).
- **Most important open risk (backend, not changed):** `POST auth/refresh-token` issues a new token pair from an access token alone, so a stolen access token can be renewed indefinitely, even after a password change (High, confirmed on the live API).

## B. Environment

| Item | Value |
|---|---|
| Device | Xiaomi M2101K6G (Redmi Note 10, codename `sweet`), **physical device** over wireless ADB, identity verified with `getprop` (not an emulator; no AVD for this model was used) |
| OS | Android 13, API 33 (MIUI) |
| App | `com.thedavelopers.eventqr` 1.0 (versionCode 1), debug build, minSdk 26 / target 35 / compile 36 |
| Backend | Live `https://eventqr-backend-owoa.onrender.com/api/v1/`; DB checks read-only via Supabase MCP (project EventQR) |
| Build | Gradle 9.4.1, AGP 9.2.1, Kotlin 2.2.10 — `assembleDebug` OK |
| Tools | adb (install, input, uiautomator dump, screencap, logcat, dumpsys), Gradle JUnit/Robolectric unit tests, Android lint, SQL checks, one scripted API check |
| Accounts | Supplied test accounts: attendee (role ORGANIZER in DB), staff, organizer, admin, superadmin |

## C. Feature Inventory

There are 58 app screens across 4 portals. Every role also gets the Attendee portal; SUPER_ADMIN uses the Admin screens plus Create Admin. Full screen-by-screen map, controls, endpoints and validation rules: `MyFiles/audit/inventory` (agent output, summarized here).

| Area | Entry point | Expected behavior | Status |
|---|---|---|---|
| Launch / session restore | Launcher → Landing | Route by saved role; background token refresh | Implemented, tested |
| Login / Sign up / Terms | Landing buttons | Validation, role routing, remembered email | Implemented, tested |
| Forgot / reset (6-digit code) / new password | Login "Forgot Password?" | Email code flow, resend rate limit | Implemented, not run on device (sends real email) |
| Change password | Profile → Edit Profile → Change Password | Policy validation | Implemented, not run (shared account) |
| Portal switcher / sign out | Dashboard chip | Switch role portal; sign out clears stack | Implemented, tested |
| Attendee home, events, search, filters | Home / Events tab | Browse approved events | Implemented, tested |
| Event detail, register, cancel | Event card | Register with terms; cancel within window | Implemented, tested |
| QR credential / QR display / download | After register; Registered tab "Show QR" | Show and save QR PNG | Implemented, tested |
| Attendee rewards, reward details, claims | Rewards tab | View balance and rewards (no in-app redeem; staff redeem) | Implemented, tested (view only) |
| Transaction history | Home / Profile | Per-event history | Implemented, tested |
| Request event, my requests, request detail | Home "Request Event" | Submit for admin review | Implemented, tested |
| Notifications inbox | Bell | List, mark read / all read | Implemented, tested |
| Profile / edit profile | Profile tab | Edit name/phone | Implemented, tested |
| Staff dashboard, scanner (camera + manual), scan result, transaction result | Staff portal | Verify QR/ID, log transaction | Verify tested; logging NOT RUN (no active event) |
| Reward redemption scan | Scanner with reward purpose | Staff redeems rewards | NOT RUN (no active event) |
| Staff assigned events, attendees, ID print (single/batch), logs | Staff tabs | Lists, print via system print | Lists tested; printing NOT RUN |
| Organizer dashboard, events, hub | Organizer portal | Stats, manage events | Tested |
| Edit event details / banner | Hub | Edit title/desc/venue/capacity | Tested (banner upload not run) |
| Staff assignment / user search / permissions | Hub | Assign staff | Viewed; assignment not run (changes global role) |
| Scan purposes, transaction rules, ID display settings | Hub | Configure scanning | Purposes + rules tested; ID settings not run |
| Attendees, search, attendee details, logs | Attendees tab | View attendees | Tested |
| Reports (8 types) + preview + CSV/PDF export | Reports tab | Generate and export | Roster + CSV tested |
| Organizer rewards (enable, CRUD) | Rewards tab | Manage rewards | Tested |
| Organizer notifications | Bell | Filtered inbox | Not run separately |
| Admin dashboard, event requests, approve/reject/upgrade | Admin portal | Review requests | Reject tested; approve/upgrade NOT RUN |
| Account management, enable/disable/delete | Accounts tab | Manage users | Search/filter tested; mutations NOT RUN |
| Create admin (super admin) | Accounts | Create ADMIN account | Validation tested; creation not run |
| Audit logs | Logs tab | Paged audit trail | Tested |

Not implemented: push/local notifications, deep links, background work, offline mode. 20 `ApiService` methods are unused (dead code; e.g. attendee self-redeem `POST rewards/redeem` has no UI).

## D. Test Results

Statuses: PASS / FAIL / BLOCKED / NOT RUN / NEEDS REVIEW. All PASS results were observed on the device; many were also cross-checked in the database. Full log with every row: `MyFiles/audit/results.md`.

| ID | Feature | Test scenario | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| B-1 | Build | assembleDebug | builds | BUILD SUCCESSFUL | PASS | gradle |
| B-2 | Unit tests | testDebugUnitTest | all pass | 498/498 | PASS | gradle |
| B-3 | Lint | lintDebug | 0 errors | 0 errors, ~1037 warnings | PASS | lint xml |
| L-1 | Launch | cold start after install | Landing, no crash | Landing, 1.67 s | PASS | am start -W |
| L-2 | Process death | EventDetail → Home → am kill → relaunch | screen restored | restored, no crash | PASS | dump, logcat |
| A-1..3 | Login validation | empty password / wrong password / bad email | errors | correct inline errors / "Invalid email or password" | PASS | 02-04 |
| A-4 | Login | valid creds, double tap | single login, role routing | routed correctly | PASS | dump |
| S-1/S-2 | Session / sign out | relaunch; sign out + Back | stays signed in; stack cleared | as expected | PASS | dumpsys |
| RG-1..4 | Sign up | weak pw, mismatch, duplicate email (double tap) | blocked / errors / rejected | as expected; DB unchanged | PASS | 61, 62, DB |
| RG-5 | Sign up | system Back | return to Login | app exits to launcher | FAIL (Low, not fixed) | focus |
| E-1..4 | Events | list, search, chips, detail | correct data | matches DB | PASS | dump, DB |
| R-1/R-2 | Event registration | terms gate; submit double tap | 1 registration + QR | 1 row, count 1, 1 QR | PASS | DB |
| R-3 | Counts after register | close QR → detail/list | 1 / 12 | still 0 / 12 | FAIL → fixed, PASS | dump |
| R-4 | Cancel registration | Yes, cancel | cancelled, QR revoked, count 0 | DB correct; UI still 1 / 12 | FAIL → fixed, PASS | DB, dump |
| R-5/R-6 | Re-register; Registered tab | — | works | works | PASS | dump |
| Q-1/Q-2 | QR download; rotation | — | saved; state kept | PNG saved; no crash | PASS | adb ls |
| W-1/W-2 | Attendee rewards; claims | — | balance + empty states | correct | PASS | dump |
| NT-1 | Notifications | mark all read | badge cleared | cleared | PASS | dump |
| T-1 | Transaction history | — | matches DB | matches | PASS | dump |
| RQ-1 | Request event | empty submit | inline errors | shown | PASS | 23 |
| RQ-2 | Request event | only contact invalid | scroll to that field | scrolled to top section | FAIL → fixed, PASS | 26, 72 |
| RQ-3 | Request event | contact "12" | rejected | accepted and stored | FAIL → fixed, PASS | DB, 73 |
| P-2 | Edit profile | blank phone | error | error | PASS | 28 |
| P-3 | Edit profile | blank name | rejected | "Profile updated successfully", blank name saved locally | FAIL → fixed, PASS | 29, 71 |
| ST-1..3 | Staff dashboard / scanner | — | loads; camera preview | as expected | PASS | 30, 31 |
| ST-4 | Scanner | blank manual Verify | clear message | "QR payload format is invalid." | FAIL (copy) → fixed, PASS | 32, 75 |
| ST-5/ST-6 | Scanner | #999 / duplicate #001 | rejected with reason | correct reasons | PASS | 33, 34 |
| ST-7 | Scan result | header Back | back to scanner | second Scanner pushed | FAIL → fixed, PASS | dumpsys |
| ST-8..10 | Staff details / attendees / logs | — | match DB | match | PASS | 35-37, DB |
| O-1..4 | Organizer dashboard, hub, edit event | stats; capacity 0; capacity save | correct; error; persisted | as expected | PASS | 38-41, DB |
| O-5..7 | Scan purpose; transaction rules | add; save | persisted | persisted | PASS | DB |
| O-8 | Organizer Rewards | early pull-to-refresh | no crash | FATAL UninitializedPropertyAccessException | FAIL → fixed, PASS | crash_N1_rewards.txt |
| O-9 | Reward dialog | invalid input | dialog stays | closed, input lost | FAIL → fixed, PASS | 48, 70 |
| O-10..12 | Rewards CRUD | create unlimited, edit, remove | persisted | persisted, description kept | PASS | DB |
| O-13/O-14 | Reports | roster + CSV export | preview + file | file in Downloads, correct | PASS | 52, adb |
| O-15 | Reports | event selection shared across tabs | — | Reports ignores Rewards selection | NEEDS REVIEW | 50 |
| O-16 | Attendees tab | counts | match roster | match | PASS | dump |
| OBS-N4 | Organizer Reports/Rewards | event list load fails | error + retry | "No events" while API had 13 | FAIL → fixed (unit-tested; failure not reproducible on demand) | dump, API |
| AD-1..5 | Admin dashboard, requests, reject, accounts, audit | — | correct + persisted | as expected | PASS | 54-57, DB |
| AD-6 | Request chips | — | All selected on open | none shown selected | NEEDS REVIEW | dump |
| SA-1/SA-2 | Super admin | Create Admin visible; invalid email/phone | errors | as expected | PASS | 58 |
| SA-3 | Create admin | phone typed as hint | accepted | rejected | FAIL → fixed (unit-tested) | 59 |
| RG-2 | Sign up terms row | tap label | toggles | only the box toggles | NEEDS REVIEW | dump |
| L-3 | Offline handling | — | — | not run: wireless adb would drop | NOT RUN | — |
| REL-1/2 | Release build | assembleRelease (R8), debug-key signed, installed over debug; flows below run on it | installs with data kept; no R8 breakage | 4.9 MB, session kept, cold start 0.88 s, no crash | PASS | release_build.log |
| AD-7 | Admin approve | throwaway organizer's request | APPROVED; requester upgraded | APPROVED; role → ORGANIZER automatically | PASS | DB |
| AD-8..10 | Admin disable/enable/delete | throwaway account | toggles; disabled can't log in; deleted | Inactive/Active; "Account is disabled. Contact support."; removed from DB | PASS | 83, DB |
| EV-1/2 | Live event | organizer sets 5 purposes, reward, staff; scheduler | ACTIVE at start | ACTIVE within 60 s | PASS | DB |
| EV-3..6 | Staff entry, booth, redeem, duplicate | #001 on ACTIVE event | logged; +10; −5 and stock 3→2; duplicate rejected | all match DB; duplicate REJECTED with 0 pts | PASS | 87-90, DB |
| EV-7 | ID print without template | Print ID before ID settings saved | clear print error | "Verification Rejected" for a print failure | FAIL → fixed (unit-tested) | 91 |
| EV-8 | ID print | after ID settings saved | print dialog with card | system dialog; PDF preview shows QR + name | PASS | 96 |
| EV-9 | Print cancel | cancel system print dialog | not reported as printed | "ID Print Sent", print logged | FAIL → app copy fixed; server still logs (backend change proposed) | 97, DB |
| EV-10 | Staff exit | exit scan | EXITED | EXITED | PASS | DB |
| PW-1..3 | Forgot / reset password | send code; wrong code; real code (user-supplied) | emailed; rejected; reset, code single-use | as expected; old password 401; app login with new password OK (correct-code step via API: the reset screen can't be reopened without re-sending) | PASS | 86, API |
| PW-4 | Change password | wrong current / valid | error / success | "Current password is incorrect" / "Password changed successfully" | PASS | 93, 94 |
| UI-1 | Instrumented UI tests | `connectedDebugAndroidTest` (11) | pass | 11/11 PASS (after MIUI "display pop-up windows while in background" was allowed) | PASS | connected_tests.log |
| L-4/L-5 | Push notifications; deep links | — | — | not implemented | N/A | manifest |
| SEC-1 | Backend refresh-token | access token only | rejected | new token pair issued | FAIL (High, backend, not fixed) | scripted check |

## E. Confirmed Defects

| # | Severity | Defect | Repro | Root cause | Files | Fix | Regression |
|---|---|---|---|---|---|---|---|
| D1 | High | Organizer Rewards crash | Organizer → Rewards tab, pull down immediately | `lateinit selectedEvent` read in `loadRewards` before the event list loads | `organizer/rewards/ManageRewardsActivity.kt` | Refresh disabled until an event resolves; guard in `loadRewards` | Device PASS (4 tries) + unit tests |
| D2 | High | Transaction Rules can save defaults over real rules | Load fails → screen shows defaults → Save | Load failure rendered as default rule | `organizer/transactions/TransactionRulesActivity.kt` | Error + Retry, Save disabled on load failure | Unit tests (failure not reproducible on device) |
| D3 | Medium | Stale "N / capacity registered" | Register or cancel, return to detail/list | Count drawn once in `onCreate`; availability response ignored; list never reloads | `EventDetailActivity.kt`, `AttendeeEventsActivity.kt`, `EventsPresenter.kt` | Redraw from availability; refresh on resume; silent list reload; cancel stale requests | Device PASS both directions + unit tests |
| D4 | Medium | Duplicate scanner on Back | Scan → result → header Back | Header back started a new `ScannerActivity` | `staff/result/StaffScanResultActivity.kt` | `finish()` | Device PASS (single scanner) |
| D5 | Medium | Contact number "12" accepted | Request Event with contact "12" | Only non-blank check; backend also unvalidated | `attendee/RequestEventActivity.kt`, new `core/util/PhoneInput.kt` | Validate/normalize to `+63…` | Device PASS + unit tests |
| D6 | Medium | False "Profile updated" for blank name | Edit Profile, clear name, Save | Client sent blank; server ignored it silently; client saved it locally | `attendee/AttendeeEditProfileActivity.kt` | Required-name validation | Device PASS + unit test |
| D7 | Medium | Organizer screens show "no events" on load failure | Event list request fails | `getApprovedOrganizerEvents()` turned errors into an empty list | `OrganizerRepository.kt` + 11 organizer screens | Error state with Retry; loading screen instead of blank window | Unit tests; device smoke PASS |
| D8 | Low | Reward dialog loses input | Save with empty title | Dialog auto-dismissed | `ManageRewardsActivity.kt` | Stays open, inline errors, locked during save, dismissed on success only | Device PASS + unit tests |
| D9 | Low | Scroll-to-error wrong target | Request Event with one late error | Offset relative to wrong parent | `RequestEventActivity.kt` | `offsetDescendantRectToMyCoords` | Device PASS + unit test |
| D10 | Low | Create Admin hint format rejected | Type "+63 912 345 6789" | Spaces not stripped | `admin/users/CreateAdminAccountActivity.kt` | `PhoneInput` normalization | Unit tests |
| D11 | Low | Misleading blank manual-scan message | Verify with empty ID | Blank treated as invalid payload | `staff/scanner/ScannerActivity.kt` | New message | Device PASS |
| D12 | Low | Filter chips ignore search text (pre-existing) | Search, then tap chip | Chip rendered unfiltered list | `AttendeeEventsActivity.kt` | Chip + search combined | Unit test |
| D13 | Low | Back on Sign Up exits app | Landing → Sign In → Create Account → Back | Login finishes when opening Sign Up | `auth/login/LoginActivity.kt` | **Fixed in Round 3** — Back returns to Landing, Sign In and Sign Up replace each other, login clears history | Emulator BS-1..BS-6 |
| D15 | Low | Print failure shown as "Verification Rejected" | Print ID on an event with no saved ID template | Scan-result screen reused the verification-failure state | `staff/result/StaffScanResultActivity.kt` | "ID Print Failed" state with server reason | Unit tests |
| D16 | Low | Cancelled print reported as sent | Cancel the system print dialog | Success shown when the dialog opened; server log written before printing | `idprinting/AndroidIdPrinter.kt`, `StaffScanResultActivity.kt`, `StaffAttendeeDetailsActivity.kt`, `EventRegistrationsActivity.kt` | Outcome read from the print job (sent/cancelled/failed) and copy follows it. **Server still logs a cancelled print**; backend confirm call proposed | Unit tests; device: cancel shows "Nothing was printed..." (toast truncated), Save-as-PDF saved id_card.pdf with no cancel message |
| D17 | Medium (backend) | Staff points/redeem and redemption binding gaps; guessable QR; no server phone validation | see G | see G | `EventQRBackend` rewards/staff controllers and services, V40/V41 migrations, `@PhilippinePhone` | Fixed in code, 948 tests pass, **merged and deployed (#332, V40/V41 applied 2026-10-16)** | CI `Backend verify` green, Postgres suite runs |
| D14 | High (backend) | Access token self-renewal | `POST auth/refresh-token` with only an access token | Endpoint mints tokens from the access token; password change doesn't revoke access tokens | Backend `AuthController.java:141-145`, `AuthService.java:96-103` | **Fixed in Round 3 (#333)** — refresh-token self-renewal route removed, password change/reset revokes every access token | CI: 948 backend tests pass (Postgres suite included) |

## F. Potential Issues (not confirmed on device)

- Report preview passes whole reports as Intent extras; "Generate All Reports" on a large event may hit `TransactionTooLargeException` (static N2, LIKELY).
- Staff Print ID prints a card without a QR if the QR lookup fails, and logs the print; no double-tap guard (N5).
- After a password change the app keeps a revoked refresh token, so the user is logged out about an hour later (N6).
- Full-size poster decoding, a possible out-of-memory on low-RAM devices (N8). QR bitmaps are built on the main thread (P2).
- QR save always fails on Android 8–9 (storage permission never requested; no crash).
- Report subtitle mismatches (N9); "max scans 0 = unlimited" not explained (N10); "Require assigned staff" toggle has no effect (N11).
- Admin request screen replaces server errors with generic text (N12); stacked toasts on partial dashboard failure (N13).
- Attendee-portal inbox for an ORGANIZER account also shows organizer notifications (NT-1 note).
- Event selection isn't shared between organizer tabs (O-15); admin request chips show no selected state (AD-6); sign-up terms label isn't a tap target (RG-2).
- Hardcoded English strings in several screens (N16); ~1037 lint warnings (36 ContentDescription, 43 Autofill, 62 HardcodedText).

## G. Security and Reliability Findings

Full list: `MyFiles/audit/security_summary.md`. No secrets were found in the mobile tree.

- **High (backend):** access-token self-renewal via `POST auth/refresh-token`; password change/reset doesn't revoke access tokens — **fixed in Round 3 (#333)**: the refresh-token self-renewal route is gone and password change/reset revokes all access tokens.
- **Medium (backend):** role demotion keeps the old role in the JWT for up to 1 h — **fixed in Round 3**: role changes return 401 on the next call and the app refreshes and reroutes.
- **Fixed and deployed (#332 on 2026-10-16, #333 on 2026-10-17):**
  - Staff points/redeem now need `can_manage_rewards`; redemptions are bound to a fresh, approved, unused redemption scan by the same staff member (staff id from the token, not the body); points are bounded; an active registration is required.
  - V40 replaces the guessable `EVTQR-<ids>` QR values with random `EVQR-` values and reissues existing ones. **Every existing QR, downloaded image or printed badge stops working.**
  - V41 adds a single-use index for redemption scans.
  - Phone numbers are validated and normalized server-side to `+63XXXXXXXXXX`.
  - Design: `MyFiles/audit/backend_security_design.md`. Code review approved; follow-ups applied.
- **Medium (mobile):** QR PNG (named with the registration ID) and report exports containing attendee PII are written to public storage.
- **Low:** no certificate pinning / network security config; FileProvider shares the whole cache folder; no `FLAG_SECURE` on QR screens; the selected-event preference isn't cleared on logout; duplicate sign-up reveals that an email is registered. (`GET /events/{id}/id-template` was on this list; it is authorized since Round 3.)
- **OK:** encrypted token storage; server-side logout; refused refresh → login; offline doesn't log out; backup and device transfer excluded; only the launcher is exported; no HTTP logging in release; server authorization enforced per endpoint; report generation is not cached (old cache finding resolved).
- **Reliability:** no crash telemetry, no offline mode or retry; one crash found and fixed (D1).

## H. Changes Made

All under `EventQRMobile/app/src/`; committed and merged in #332/#333. Implemented by mobile-developer; reviewed three times by code-reviewer (final: approve, with follow-ups applied).

| File | Reason | Effect |
|---|---|---|
| `main/.../organizer/rewards/ManageRewardsActivity.kt` | D1, D7, D8 | No crash on early refresh; load-error state with Retry; dialog validation, locked while saving, dismissed in `onDestroy` |
| `main/.../organizer/transactions/TransactionRulesActivity.kt` | D2, D7 | Load failure → error/Retry, Save disabled |
| `main/.../attendee/events/EventDetailActivity.kt` | D3 | Live capacity from availability; refresh on resume; stale requests cancelled |
| `main/.../attendee/events/AttendeeEventsActivity.kt`, `EventsPresenter.kt` | D3, D12 | Silent reload on resume (never overrides a visible load); chips respect search |
| `main/.../staff/result/StaffScanResultActivity.kt` | D4 | Header back = `finish()` |
| `main/.../core/util/PhoneInput.kt` (new) | D5, D10 | Strip formatting, validate with existing `Validators`, return `+63…` |
| `main/.../attendee/RequestEventActivity.kt` | D5, D9 | Contact number validation; correct scroll-to-error |
| `main/.../attendee/AttendeeEditProfileActivity.kt` | D6 | Name required |
| `main/.../admin/users/CreateAdminAccountActivity.kt` | D10 | Accepts hint format; sends normalized phone |
| `main/.../staff/scanner/ScannerActivity.kt` | D11 | Blank manual-entry message |
| `main/.../organizer/OrganizerRepository.kt`, `shared/OrganizerUiStateHelpers.kt`, `shared/OrganizerScreenHelpers.kt` | D7 | `loadApprovedOrganizerEvents()` distinguishes errors; shared loading/error screens |
| `main/.../organizer/reports/EventReportsActivity.kt`, `OrganizerReportsRepository.kt`, `transactions/TransactionLogsActivity.kt`, `scanpurposes/ManageScanPurposesActivity.kt`, `staff/ManageUsersActivity.kt`, `staff/SearchUserAccountActivity.kt`, `attendees/{SearchAttendees,AttendeeDetails,AttendeeManagement}Activity.kt`, `notifications/NotificationManagementActivity.kt` | D7 | Error + Retry instead of "no events"; no blank window |
| `main/.../attendee/AttendeeRepository.kt`, `admin/AdminRepository.kt` | tests | A few methods made `open` for test fakes (no behavior change) |
| `main/res/values/strings.xml` | all | New error/loading strings; one unused string removed |
| `test/...` (new and extended suites) | all | Regression tests for every fix |

Test data created on the live backend: attendee registration on "ZZ C-test (safe to delete)" (still active); event request "ZZ Audit Request safe to reject" (rejected); ZZ capacity 12→13→12; scan purpose, rule and reward on ZZ (all removed). On the phone: a QR PNG in `Pictures/EventQR` and `mang-inasal_roster_2026-10-15.csv` in `Download`.

## I. Regression Results

| Layer | Result |
|---|---|
| Build | `assembleDebug` BUILD SUCCESSFUL |
| Unit tests (JVM + Robolectric) | 555 passed, 0 failures (baseline 498) |
| Lint | 0 errors; 1032 warnings (was 1037) |
| Instrumented UI tests | New `smoke/AuthSmokeTest` (10) + template test: **11/11 PASS** on the Xiaomi |
| Backend tests | CI `Backend verify`: 948 passed, 0 failures, 0 skipped (the Testcontainers Postgres suite runs in CI; locally it skips without Docker) |
| Device (fixed build) | 11 regression checks: 10 PASS; the 1 FAIL (OBS-N4) was then fixed and the touched organizer screens smoke-tested PASS with no FATAL in logcat |

## J2. Round 3 (2026-10-17): decisions applied

- **Status of earlier fixes:** PR #332 is pushed and deployed. V40/V41 were applied by the deploy (2026-10-16 20:37 UTC). Live QR values are all random `EVQR-`, the single-use index exists, and the server rejects bad phone numbers.
- **ID printing, backend:** a default template (QR + name) is used when none is saved; GET id-template is now authorized (admin, owner, assigned staff). Print logs are created PENDING and confirmed via `PATCH /staff/events/{eventId}/print-logs/{logId}`. Only PRINTED logs count; a PENDING log older than 10 minutes counts as PRINTED for old builds. Migration V42 (`status`, `confirmed_at`, `printed_by_user_id`, check, index).
- **ID printing, app:** confirms each print outcome (with retry), refreshes the Print/Reprint label, shows short cancel/failed copy, and falls back to the default template.
- **Token self-renewal (H1), backend:**
  - `/auth/refresh-token` and `PATCH /auth/me/password` removed.
  - Password change/reset revokes every access token at once (ms marker, `iatMs` claim, race-safe upsert); change-password returns a fresh session.
  - Rotation retry window is 60 s (configurable) and keeps a single live branch per family; a 90-day absolute session lifetime (V43).
  - Change-password is rate-limited.
  - Role changes return 401 on the next call (M1), and the app refreshes and reroutes.
- **Token self-renewal (H1), app:** a single shared refresh rotator, a guard while a password change is in flight, and the role picked up on every refresh.
- **Back navigation (D13):** Back from Sign In or Sign Up returns to Landing; Sign In and Sign Up replace each other; login clears the history; sign-out returns to Sign In with Landing beneath. Verified on the emulator (BS-1..6).
- **Reviews:** code + security review, two follow-up rounds, final verification: PUSH.
- **Tests:** backend 948 pass in CI (Postgres suite included, 0 skipped); app 604 pass; the upsert SQL was EXPLAIN-checked on live Postgres 17.
- **Release notes:** ship app and backend together. Old builds can't pick up role changes or survive a password change without re-login. V42 and V43 run on deploy.
- **After deploy, verify:**
  - One password change, then old token → 401.
  - One account suspend.
  - One print + cancel, then check `id_print_logs.status`.
- **Open:** `can_print_id` is still not enforced — the print and template routes require an active assignment (or owner/admin) via `IdPrintingAccess`, not that flag (product decision); offline not tested; live backend was failing to wake on Render at the end of the session (`hibernate-wake-error`).

## J2b. Round 4 (2026-10-17): fresh-database and CI repair

Prompted by PR #333's backend job, which was red on `main` since #332. Two defects, both fixed and merged:

- **Fresh empty database could not migrate at all:** Flyway died at `V2` (`42P01 relation "events" does not exist`) because `V2`..`V15` patch tables that only `V16__baseline_schema.sql` creates. `FreshDatabaseBootstrapCallback` already builds that baseline first, but it was a `@Component` and `@DataJpaTest` slices do not component-scan, so the Postgres tests — the only place a fresh database is built — never ran it. It is now registered with `@Import` on `EventqrApplication` (#334).
- **A fresh database was stricter than production:** the V17/V18 guards skip `fk_point_transactions_source` and `uq_txn_logs_approved_reg_purpose` in production (legacy drift), but an empty database satisfies them and got both, so reward redemption failed with `23503`/`23505` on any fresh install or DR restore. `R__align_fresh_schema_with_production.sql` (repeatable, not versioned — a new version number before V42/V43 landed would have broken the next deploy under `out-of-order=false`) drops both, and `RewardRedemptionPostgresTest` now asserts the fresh-vs-production parity catalogue.
- **Consequence for verification:** the backend Postgres suite now actually runs in CI instead of being skipped locally, so "fixed in code, Postgres unverified" is no longer the ceiling for migration work.

## J. Remaining Work

- **Needs your decision:**
  - ~~Deploy the backend fixes and run V40/V41~~ **Done.** V40/V41 were applied by the #332 deploy (2026-10-16); #333 deployed V42/V43 plus the repeatable reconciliation (2026-10-17). Existing printed badges still carry the old QR format only if they predate 2026-10-16 — re-send QRs and reprint as originally planned.
  - ID print: should a missing ID template fall back to a default (QR + name)? Should cancelled prints be logged? (Both shipped in Round 3 — a default template is used and print logs are confirmed via `PATCH /staff/events/{eventId}/print-logs/{logId}`; keep only if you want different behaviour.)
  - H1 token self-renewal and D13 auth back-stack: fixed in Round 3 — nothing left to decide unless you disagree with the shipped contract.
- **Device-checked 2026-10-17 03:51:** D16 cancel and Save-as-PDF on MIUI print service behave correctly; the cancel toast is truncated (Low, shorten copy or use a dialog). The backend was briefly down (503, 03:45-03:49) and recovered by itself.
- **Not run:** offline/network loss (wireless adb would drop); banner upload; batch ID print; PDF report export; organizer notifications screen.
- **Device setup note:** MIUI blocks instrumentation from launching activities until EventQR is allowed to "display pop-up windows while running in the background".
- **Next steps:** add crash reporting; address F items by severity.

## K. Final Assessment

The app builds, launches and its core attendee, staff, organizer and admin flows work correctly against the live backend on the Xiaomi M2101K6G (Android 13). The audit found one crash, one data-loss path and several correctness/UX defects; all in-app ones are fixed, covered by new unit tests, reviewed, and re-verified on the device where reproducible. Round 2 then covered event-day flows, the release build, password flows and admin actions; all work except two ID-print issues, now fixed in the app. A new UI test suite passes 11/11 on the device. Round 3 closed the backend security findings (H1 token self-renewal, M1 role changes) and D13, and Round 4 repaired the fresh-database migration path; the backend is deployed through #333 with V40-V43 and the `R__` reconciliation applied, and CI `Backend verify` is green again. Unverified: offline behaviour, and the Round 3/4 backend behaviour against production beyond the CI Postgres suite. Remaining known gap: `can_print_id` is not enforced (product decision).
