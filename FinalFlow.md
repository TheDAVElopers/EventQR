# EventQR End-to-End Testing Flow

This document provides a chronological end-to-end testing flow for the EventQR application.
Testers should follow the tests in order from TEST-001 to the final test, as each test builds upon the previous ones.

## Prerequisites
- The EventQR backend API is running and accessible.
- A Super Admin account exists in the system (created via external means, as the API does not provide self-registration for Super Admin roles).
- Known credentials for the pre-existing Super Admin:
  - Email: `superadmin@eventqr.com`
  - Password: `SuperAdmin@123`
  - Full Name: `Super Admin`

## Chronological Test Flow

### Phase 1: Super Admin Authentication and Initial Setup

#### TEST-001 — Super Admin Login (Happy Path)
Role: Super Admin
Type: Happy Path

Prerequisites:
- Application is running.
- Super Admin account exists with the credentials above.

Test Data:
- Email: `superadmin@eventqr.com`
- Password: `SuperAdmin@123`

Steps:
1. Open the application or API client.
2. Navigate to the login endpoint (`POST /api/v1/auth/login`).
3. Enter the test data.
4. Submit the login request.

Expected Result:
- Login is successful.
- System returns a JWT token and user details.
- User role is confirmed as `SUPER_ADMIN`.

#### TEST-002 — Super Admin Login with Invalid Password (Unhappy Path)
Role: Super Admin
Type: Unhappy Path (Authentication)

Prerequisites:
- TEST-001 must be completed successfully (Super Admin account exists).

Test Data:
- Email: `superadmin@eventqr.com`
- Password: `WrongPassword`

Steps:
1. Navigate to the login endpoint.
2. Enter the test data.
3. Submit the login request.

Expected Result:
- System rejects the login attempt.
- Appropriate error message is displayed (e.g., "Invalid credentials").
- No token is returned.

#### TEST-003 — Super Admin Login with Non-existent Email (Unhappy Path)
Role: Super Admin
Type: Unhappy Path (Authentication)

Prerequisites:
- Super Admin account exists.

Test Data:
- Email: `nonexistent@eventqr.com`
- Password: `anyPassword`

Steps:
1. Navigate to the login endpoint.
2. Enter the test data.
3. Submit the login request.

Expected Result:
- System rejects the login attempt.
- Appropriate error message is displayed (e.g., "Invalid credentials").
- No token is returned.

#### TEST-004 — Super Admin Creates Admin Account (Happy Path)
Role: Super Admin
Type: Happy Path

Prerequisites:
- TEST-001 completed successfully (Super Admin logged in and token available).

Test Data:
- Email: `admin1@eventqr.com`
- Password: `Admin@12345`
- Full Name: `Admin User`
- Role: `ADMIN`

Steps:
1. Use the Super Admin token to access the user creation endpoint (`POST /api/v1/users`).
2. Enter the test data.
3. Submit the request.

Expected Result:
- Admin account is successfully created.
- System returns the created user details with role `ADMIN`.
- The new Admin can log in with the provided credentials.

#### TEST-005 — Super Admin Creates Admin with Existing Email (Unhappy Path)
Role: Super Admin
Type: Unhappy Path (Validation)

Prerequisites:
- TEST-004 completed successfully (Admin account created).

Test Data:
- Email: `admin1@eventqr.com` (duplicate)
- Password: `Admin@12345`
- Full Name: `Admin User Two`
- Role: `ADMIN`

Steps:
1. Use the Super Admin token to access the user creation endpoint.
2. Enter the test data.
3. Submit the request.

Expected Result:
- System rejects the request due to duplicate email.
- Appropriate error message is displayed (e.g., "Email already exists").
- No new user is created.

#### TEST-006 — Super Admin Creates Admin with Missing Required Field (Unhappy Path)
Role: Super Admin
Type: Unhappy Path (Validation)

Prerequisites:
- Super Admin logged in.

Test Data:
- Email: `admin2@eventqr.com`
- Password: `Admin@12345`
- Full Name: `` (empty)
- Role: `ADMIN`

Steps:
1. Use the Super Admin token to access the user creation endpoint.
2. Enter the test data.
3. Submit the request.

Expected Result:
- System rejects the request due to missing full name.
- Appropriate error message is displayed (e.g., "Full name is required").
- No new user is created.

#### TEST-007 — Super Admin Logs Out (Happy Path)
Role: Super Admin
Type: Happy Path

Prerequisites:
- Super Admin logged in (token available).

Test Data:
- None

Steps:
1. Navigate to the logout endpoint (`POST /api/v1/auth/logout`).
2. Submit the request with the Super Admin token.

Expected Result:
- Logout is successful.
- Token is invalidated.
- Subsequent requests with the token are rejected.

### Phase 2: Admin Authentication and User Management

#### TEST-008 — Admin Login (Happy Path)
Role: Admin
Type: Happy Path

Prerequisites:
- TEST-004 completed successfully (Admin account created).

Test Data:
- Email: `admin1@eventqr.com`
- Password: `Admin@12345`

Steps:
1. Navigate to the login endpoint.
2. Enter the test data.
3. Submit the login request.

Expected Result:
- Login is successful.
- System returns a JWT token and user details.
- User role is confirmed as `ADMIN`.

#### TEST-009 — Admin Login with Invalid Credentials (Unhappy Path)
Role: Admin
Type: Unhappy Path (Authentication)

Prerequisites:
- Admin account exists.

Test Data:
- Email: `admin1@eventqr.com`
- Password: `WrongPassword`

Steps:
1. Navigate to the login endpoint.
2. Enter the test data.
3. Submit the login request.

Expected Result:
- System rejects the login attempt.
- Appropriate error message is displayed.
- No token is returned.

#### TEST-010 — Admin Creates Organizer Account (Happy Path)
Role: Admin
Type: Happy Path

Prerequisites:
- TEST-008 completed successfully (Admin logged in and token available).

Test Data:
- Email: `organizer1@eventqr.com`
- Password: `Organizer@123`
- Full Name: `Organizer User`
- Role: `ORGANIZER`

Steps:
1. Use the Admin token to access the user creation endpoint (`POST /api/v1/users`).
2. Enter the test data.
3. Submit the request.

Expected Result:
- Organizer account is successfully created.
- System returns the created user details with role `ORGANIZER`.
- The new Organizer can log in with the provided credentials.

#### TEST-011 — Admin Creates Staff Account (Happy Path)
Role: Admin
Type: Happy Path

Prerequisites:
- Admin logged in.

Test Data:
- Email: `staff1@eventqr.com`
- Password: `Staff@123`
- Full Name: `Staff User`
- Role: `STAFF`

Steps:
1. Use the Admin token to access the user creation endpoint.
2. Enter the test data.
3. Submit the request.

Expected Result:
- Staff account is successfully created.
- System returns the created user details with role `STAFF`.
- The new Staff can log in with the provided credentials.

#### TEST-012 — Admin Views All Users (Happy Path)
Role: Admin
Type: Happy Path

Prerequisites:
- Admin logged in.
- At least one Admin, Organizer, Staff created.

Test Data:
- None

Steps:
1. Use the Admin token to access the users list endpoint (`GET /api/v1/users`).
2. Submit the request.

Expected Result:
- System returns a list of users.
- List includes Super Admin, Admin, Organizer, Staff accounts.
- Pagination works correctly (if applicable).

#### TEST-013 — Admin Updates User Role (Happy Path)
Role: Admin
Type: Happy Path

Prerequisites:
- Admin logged in.
- Organizer account exists from TEST-010.

Test Data:
- User ID: (ID of organizer1@eventqr.com)
- New Role: `STAFF`

Steps:
1. Use the Admin token to access the user update endpoint (`PATCH /api/v1/users/{userId}/role/{role}` or similar).
2. Enter the user ID and new role.
3. Submit the request.

Expected Result:
- User role is successfully updated from ORGANIZER to STAFF.
- System returns the updated user details.
- The user can now log in with STAFF permissions.

#### TEST-014 — Admin Attempts to Access Super Admin Only Function (Unhappy Path)
Role: Admin
Type: Unhappy Path (Authorization)

Prerequisites:
- Admin logged in.

Test Data:
- None

Steps:
1. Attempt to access a Super Admin-only endpoint (e.g., system settings if exists, or try to create another Super Admin).
2. Submit the request.

Expected Result:
- System rejects the request with insufficient permissions error (e.g., 403 Forbidden).
- No unauthorized action is performed.

### Phase 3: Organizer Authentication and Event Management

#### TEST-015 — Organizer Login (Happy Path)
Role: Organizer
Type: Happy Path

Prerequisites:
- TEST-010 completed successfully (Organizer account created).

Test Data:
- Email: `organizer1@eventqr.com`
- Password: `Organizer@123`

Steps:
1. Navigate to the login endpoint.
2. Enter the test data.
3. Submit the login request.

Expected Result:
- Login is successful.
- System returns a JWT token and user details.
- User role is confirmed as `ORGANIZER`.

#### TEST-016 — Organizer Login with Invalid Credentials (Unhappy Path)
Role: Organizer
Type: Unhappy Path (Authentication)

Prerequisites:
- Organizer account exists.

Test Data:
- Email: `organizer1@eventqr.com`
- Password: `WrongPassword`

Steps:
1. Navigate to the login endpoint.
2. Enter the test data.
3. Submit the login request.

Expected Result:
- System rejects the login attempt.
- Appropriate error message is displayed.
- No token is returned.

#### TEST-017 — Organizer Creates Event (Happy Path)
Role: Organizer
Type: Happy Path

Prerequisites:
- Organizer logged in (token available).
- Event creation endpoint exists.

Test Data:
- Name: `Tech Conference 2026`
- Description: `Annual technology conference`
- Start Date: `2026-10-15T09:00:00Z`
- End Date: `2026-10-17T17:00:00Z`
- Location: `Convention Center`
- Capacity: `500`
- Status: `DRAFT` (or `PENDING_APPROVAL` depending on workflow)

Steps:
1. Use the Organizer token to access the event creation endpoint (`POST /api/v1/events`).
2. Enter the test data.
3. Submit the request.

Expected Result:
- Event is successfully created.
- System returns the created event details.
- Event status is as expected (DRAFT or PENDING_APPROVAL).
- Organizer can view the event in their list.

#### TEST-018 — Organizer Creates Event with Missing Required Field (Unhappy Path)
Role: Organizer
Type: Unhappy Path (Validation)

Prerequisites:
- Organizer logged in.

Test Data:
- Name: `Tech Conference 2026`
- Description: `` (empty)
- Start Date: `2026-10-15T09:00:00Z`
- End Date: `2026-10-17T17:00:00Z`
- Location: `Convention Center`
- Capacity: `500`

Steps:
1. Use the Organizer token to access the event creation endpoint.
2. Enter the test data.
3. Submit the request.

Expected Result:
- System rejects the request due to missing description.
- Appropriate error message is displayed.
- No event is created.

#### TEST-019 — Organizer Creates Event with Invalid Date (Unhappy Path)
Role: Organizer
Type: Unhappy Path (Validation)

Prerequisites:
- Organizer logged in.

Test Data:
- Name: `Tech Conference 2026`
- Description: `Annual technology conference`
- Start Date: `2026-10-17T17:00:00Z` (after end date)
- End Date: `2026-10-15T09:00:00Z`
- Location: `Convention Center`
- Capacity: `500`

Steps:
1. Use the Organizer token to access the event creation endpoint.
2. Enter the test data.
3. Submit the request.

Expected Result:
- System rejects the request due to invalid date range.
- Appropriate error message is displayed.
- No event is created.

#### TEST-020 — Organizer Views Own Events (Happy Path)
Role: Organizer
Type: Happy Path

Prerequisites:
- Organizer logged in.
- At least one event created from TEST-017.

Test Data:
- None

Steps:
1. Use the Organizer token to access the events list endpoint (`GET /api/v1/events` or `/api/v1/organizer/events`).
2. Submit the request.

Expected Result:
- System returns a list of events created by the organizer.
- The event from TEST-017 is present in the list.
- Event details are correct.

#### TEST-021 — Organizer Updates Event (Happy Path)
Role: Organizer
Type: Happy Path

Prerequisites:
- Organizer logged in.
- Event exists from TEST-017.

Test Data:
- Event ID: (ID from TEST-017)
- Name: `Tech Conference 2026 Updated`
- Description: `Updated description`
- Start Date: `2026-10-15T09:00:00Z`
- End Date: `2026-10-17T17:00:00Z`
- Location: `Updated Convention Center`
- Capacity: `600`

Steps:
1. Use the Organizer token to access the event update endpoint (`PUT /api/v1/events/{eventId}`).
2. Enter the updated data.
3. Submit the request.

Expected Result:
- Event is successfully updated.
- System returns the updated event details.
- Changes are reflected when viewing the event.

#### TEST-022 — Organizer Deletes Event (Happy Path)
Role: Organizer
Type: Happy Path

Prerequisites:
- Organizer logged in.
- Event exists (can be a test event created for this purpose).

Test Data:
- Event ID: (ID of a deletable event)

Steps:
1. Use the Organizer token to access the event deletion endpoint (`DELETE /api/v1/events/{eventId}`).
2. Submit the request.

Expected Result:
- Event is successfully deleted.
- System returns success confirmation.
- Event no longer appears in the organizer's event list.
- Attempting to access the deleted event returns not found.

#### TEST-023 — Organizer Attempts to Access Admin User Management (Unhappy Path)
Role: Organizer
Type: Unhappy Path (Authorization)

Prerequisites:
- Organizer logged in.

Test Data:
- None

Steps:
1. Attempt to access the user creation endpoint (`POST /api/v1/users`).
2. Submit the request.

Expected Result:
- System rejects the request with insufficient permissions error.
- Organizer cannot create users.

### Phase 4: Attendee Self-Registration and Event Participation

#### TEST-024 — Attendee Self-Registration (Happy Path)
Role: Attendee (self-registering)
Type: Happy Path

Prerequisites:
- Event exists and is open for registration (from Organizer's published event).
- Registration is enabled for the event.

Test Data:
- Email: `attendee1@eventqr.com`
- Password: `Attendee@123`
- Full Name: `Attendee User`
- Phone Number: `+1234567890`

Steps:
1. Navigate to the registration endpoint (`POST /api/v1/auth/register` or `/api/v1/registrations`).
2. Enter the test data.
3. Submit the request.

Expected Result:
- Attendee account is successfully created.
- System returns the created user details with role `ATTENDEE`.
- Attendee can log in with the provided credentials.
- Attendee is not automatically registered for any event (unless specified).

#### TEST-025 — Attendee Self-Registration with Existing Email (Unhappy Path)
Role: Attendee
Type: Unhappy Path (Validation)

Prerequisites:
- TEST-024 completed successfully (Attendee account created).

Test Data:
- Email: `attendee1@eventqr.com` (duplicate)
- Password: `Attendee@123`
- Full Name: `Attendee User Two`
- Phone Number: `+0987654321`

Steps:
1. Navigate to the registration endpoint.
2. Enter the test data.
3. Submit the request.

Expected Result:
- System rejects the request due to duplicate email.
- Appropriate error message is displayed.
- No new user is created.

#### TEST-026 — Attendee Self-Registration with Invalid Email (Unhappy Path)
Role: Attendee
Type: Unhappy Path (Validation)

Prerequisites:
- Registration endpoint accessible.

Test Data:
- Email: `invalid-email`
- Password: `Attendee@123`
- Full Name: `Attendee User`
- Phone Number: `+1234567890`

Steps:
1. Navigate to the registration endpoint.
2. Enter the test data.
3. Submit the request.

Expected Result:
- System rejects the request due to invalid email format.
- Appropriate error message is displayed.
- No user is created.

#### TEST-027 — Attendee Login (Happy Path)
Role: Attendee
Type: Happy Path

Prerequisites:
- TEST-024 completed successfully (Attendee account created).

Test Data:
- Email: `attendee1@eventqr.com`
- Password: `Attendee@123`

Steps:
1. Navigate to the login endpoint.
2. Enter the test data.
3. Submit the login request.

Expected Result:
- Login is successful.
- System returns a JWT token and user details.
- User role is confirmed as `ATTENDEE`.

#### TEST-028 — Attendee Views Available Events (Happy Path)
Role: Attendee
Type: Happy Path

Prerequisites:
- Attendee logged in.
- At least one event is published and open for registration (from Organizer).

Test Data:
- None

Steps:
1. Use the Attendee token to access the events browsing endpoint (`GET /api/v1/events/available` or similar).
2. Submit the request.

Expected Result:
- System returns a list of events available for registration.
- The published event from Organizer is present in the list.
- Event details are correct.

#### TEST-029 — Attendee Registers for Event (Happy Path)
Role: Attendee
Type: Happy Path

Prerequisites:
- Attendee logged in.
- Event exists and is open for registration.

Test Data:
- Event ID: (ID of an available event)

Steps:
1. Use the Attendee token to access the event registration endpoint (`POST /api/v1/registrations` or `/api/v1/events/{eventId}/register`).
2. Enter the event ID.
3. Submit the request.

Expected Result:
- Attendee is successfully registered for the event.
- System returns registration confirmation.
- Attendee's registration status is `CONFIRMED` or similar.
- Attendee can view their registration in their registrations list.

#### TEST-030 — Attendee Registers for Already Registered Event (Unhappy Path)
Role: Attendee
Type: Unhappy Path (Validation / Business Rule)

Prerequisites:
- TEST-029 completed successfully (Attendee registered for event).

Test Data:
- Event ID: (same event ID from TEST-029)

Steps:
1. Use the Attendee token to access the event registration endpoint.
2. Enter the event ID.
3. Submit the request.

Expected Result:
- System rejects the request because the attendee is already registered.
- Appropriate error message is displayed (e.g., "Already registered for this event").
- No duplicate registration is created.

#### TEST-031 — Attendee Registers for Event with Zero Capacity (Unhappy Path)
Role: Attendee
Type: Unhappy Path (Validation / Business Rule)

Prerequisites:
- Event exists but has reached maximum capacity (can be set up by creating an event with capacity 0 or filling it).

Test Data:
- Event ID: (ID of a full event)

Steps:
1. Use the Attendee token to access the event registration endpoint.
2. Enter the event ID.
3. Submit the request.

Expected Result:
- System rejects the request due to insufficient capacity.
- Appropriate error message is displayed.
- No registration is created.

#### TEST-032 — Attendee Views Own Registrations (Happy Path)
Role: Attendee
Type: Happy Path

Prerequisites:
- Attendee logged in.
- At least one registration exists from TEST-029.

Test Data:
- None

Steps:
1. Use the Attendee token to access the registrations list endpoint (`GET /api/v1/registrations` or `/api/v1/attendee/registrations`).
2. Submit the request.

Expected Result:
- System returns a list of the attendee's registrations.
- The registration from TEST-029 is present in the list.
- Registration details are correct (event info, status, etc.).

#### TEST-033 — Attendee Cancels Registration (Happy Path)
Role: Attendee
Type: Happy Path

Prerequisites:
- Attendee logged in.
- Registration exists from TEST-029 (and cancellation is allowed).

Test Data:
- Registration ID: (ID of the registration)

Steps:
1. Use the Attendee token to access the registration cancellation endpoint (`DELETE /api/v1/registrations/{registrationId}` or similar).
2. Submit the request.

Expected Result:
- Registration is successfully cancelled.
- System returns success confirmation.
- Registration no longer appears in the attendee's list.
- Attendee can register for the event again (if still open).

### Phase 5: Staff and Scanning Operations

#### TEST-034 — Staff Login (Happy Path)
Role: Staff
Type: Happy Path

Prerequisites:
- TEST-011 completed successfully (Staff account created).

Test Data:
- Email: `staff1@eventqr.com`
- Password: `Staff@123`

Steps:
1. Navigate to the login endpoint.
2. Enter the test data.
3. Submit the login request.

Expected Result:
- Login is successful.
- System returns a JWT token and user details.
- User role is confirmed as `STAFF`.

#### TEST-035 — Staff Views Assigned Events (Happy Path)
Role: Staff
Type: Happy Path

Prerequisites:
- Staff logged in.
- Staff has been assigned to at least one event (may need assignment by Admin or Organizer; if not directly available, we may skip or assume staff can see all events for scanning).

Test Data:
- None

Steps:
1. Use the Staff token to access the staff events endpoint (`GET /api/v1/staff/events` or similar).
2. Submit the request.

Expected Result:
- System returns a list of events assigned to the staff.
- If no direct assignment endpoint exists, staff may be able to scan for any event; we'll check the scanning feature.

#### TEST-036 — Staff Performs QR Code Scanning (Check-in) (Happy Path)
Role: Staff
Type: Happy Path

Prerequisites:
- Staff logged in.
- Event exists with at least one registered attendee (from TEST-029).
- QR code for the attendee is available (can be simulated or we check the scanning logic).

Test Data:
- Attendee QR Code Data: (encoded attendee ID or registration ID for the event)

Steps:
1. Use the Staff token to access the scanning endpoint (`POST /api/v1/scan` or similar).
2. Enter the QR code data.
3. Submit the request.

Expected Result:
- System validates the QR code.
- Attendee is checked in for the event.
- System returns check-in confirmation.
- Attendee's attendance status is updated to `PRESENT` or similar.
- Duplicate scans for the same attendee may be prevented or logged appropriately.

#### TEST-037 — Staff Scans Invalid QR Code (Unhappy Path)
Role: Staff
Type: Unhappy Path (Validation)

Prerequisites:
- Staff logged in.

Test Data:
- QR Code Data: `invalid-qr-code-data`

Steps:
1. Use the Staff token to access the scanning endpoint.
2. Enter the invalid QR code data.
3. Submit the request.

Expected Result:
- System rejects the request due to invalid QR code.
- Appropriate error message is displayed.
- No check-in is recorded.

#### TEST-038 — Staff Scans QR Code for Non-registered Attendee (Unhappy Path)
Role: Staff
Type: Unhappy Path (Validation / Business Rule)

Prerequisites:
- Staff logged in.
- QR code for an attendee not registered for the event.

Test Data:
- QR Code Data: (encoded ID of an attendee not registered for the event)

Steps:
1. Use the Staff token to access the scanning endpoint.
2. Enter the QR code data.
3. Submit the request.

Expected Result:
- System rejects the request because the attendee is not registered for the event.
- Appropriate error message is displayed.
- No check-in is recorded.

### Phase 6: Advanced Features and Administrative Oversight

#### TEST-039 — Admin Views Dashboard Statistics (Happy Path)
Role: Admin
Type: Happy Path

Prerequisites:
- Admin logged in.
- Some data exists in the system (users, events, registrations).

Test Data:
- None

Steps:
1. Use the Admin token to access the dashboard endpoint (`GET /api/v1/dashboard`).
2. Submit the request.

Expected Result:
- System returns dashboard statistics.
- Statistics include user counts, event counts, registration counts, etc.
- Data is accurate and up-to-date.

#### TEST-040 — Admin Generates Report (Happy Path)
Role: Admin
Type: Happy Path

Prerequisites:
- Admin logged in.
- Reporting feature exists.

Test Data:
- Report Type: `registration_summary`
- Event ID: (ID of an event with registrations)
- Date Range: (optional)

Steps:
1. Use the Admin token to access the report generation endpoint (`POST /api/v1/reports` or similar).
2. Enter the test data.
3. Submit the request.

Expected Result:
- Report is successfully generated.
- System returns the report data or a download link.
- Report contains accurate information based on the selected criteria.

#### TEST-041 — Admin Views Audit Logs (Happy Path)
Role: Admin
Type: Happy Path

Prerequisites:
- Admin logged in.
- Audit logging is enabled.
- Some actions have been performed in the system (logins, creations, updates).

Test Data:
- None

Steps:
1. Use the Admin token to access the audit logs endpoint (`GET /api/v1/auditlogs`).
2. Submit the request.

Expected Result:
- System returns a list of audit log entries.
- Entries include user actions, timestamps, and details.
- Logs are accurate and tamper-evident.

#### TEST-042 — Admin Manages Rewards (Happy Path)
Role: Admin
Type: Happy Path

Prerequisites:
- Admin logged in.
- Rewards feature exists.

Test Data:
- Name: `Early Bird Discount`
- Description: `10% off for early registration`
- Criteria: `registration_date_before: 2026-09-01`
- Reward Type: `DISCOUNT`
- Value: `10`

Steps:
1. Use the Admin token to access the rewards creation endpoint (`POST /api/v1/rewards`).
2. Enter the test data.
3. Submit the request.

Expected Result:
- Reward is successfully created.
- System returns the created reward details.
- Reward can be assigned to events or users.

#### TEST-043 — Super Admin Manages System Settings (Happy Path)
Role: Super Admin
Type: Happy Path

Prerequisites:
- Super Admin logged in.
- System settings endpoint exists.

Test Data:
- Setting Key: `event_registration_opens_days_before`
- Setting Value: `30`

Steps:
1. Use the Super Admin token to access the system settings endpoint (`POST /api/v1/system/settings` or similar).
2. Enter the test data.
3. Submit the request.

Expected Result:
- System setting is successfully updated.
- System returns confirmation.
- The setting affects the application behavior as expected.

#### TEST-044 — Super Admin Views All Admins (Happy Path)
Role: Super Admin
Type: Happy Path

Prerequisites:
- Super Admin logged in.
- At least one Admin account exists.

Test Data:
- None

Steps:
1. Use the Super Admin token to access the admins list endpoint (`GET /api/v1/admins` or via users with role filter).
2. Submit the request.

Expected Result:
- System returns a list of Admin users.
- List includes the Admin from TEST-004.
- Details are correct.

### Phase 7: Edge Cases and Cleanup

#### TEST-045 — Attempt to Access Endpoint without Authentication (Unhappy Path)
Role: Unauthenticated User
Type: Unhappy Path (Authentication)

Prerequisites:
- No authentication token.

Test Data:
- None

Steps:
1. Attempt to access a protected endpoint (e.g., `GET /api/v1/users`) without any token.
2. Submit the request.

Expected Result:
- System rejects the request with missing authentication error (e.g., 401 Unauthorized).
- No data is returned.

#### TEST-046 — Attempt to Access Endpoint with Expired Token (Unhappy Path)
Role: Any
Type: Unhappy Path (Authentication)

Prerequisites:
- A valid token exists but has been expired (may need to wait or simulate).

Test Data:
- Expired JWT token.

Steps:
1. Attempt to access a protected endpoint with the expired token.
2. Submit the request.

Expected Result:
- System rejects the request with invalid/expired token error.
- No data is returned.

#### TEST-047 — Concurrent Registration for Same Edge Case (Unhappy Path)
Role: Attendee
Type: Unhappy Path (Data Integrity)

Prerequisites:
- Event exists with exactly one slot left (capacity = registered + 1).

Test Data:
- Two different attendees trying to register at the same time.

Steps:
1. Attendee A and Attendee B both attempt to register for the event simultaneously.
2. Both submit registration requests.

Expected Result:
- Only one attendee succeeds in registration.
- The other receives an error indicating the event is full.
- Final registration count matches the event capacity.
- No overbooking occurs.

#### TEST-048 — Logout and Subsequent Access Attempt (Unhappy Path)
Role: Any
Type: Unhappy Path (Authentication)

Prerequisites:
- User logged in.

Test Data:
- None

Steps:
1. User logs out via the logout endpoint.
2. Attempt to access a protected endpoint using the same token.

Expected Result:
- System rejects the request because the token is invalidated.
- Appropriate error message is displayed.
- No data is returned.

#### TEST-049 — Password Change (Happy Path)
Role: Any User
Type: Happy Path

Prerequisites:
- User logged in.

Test Data:
- Current Password: `currentPassword123`
- New Password: `newPassword@123`
- Confirm New Password: `newPassword@123`

Steps:
1. Use the user token to access the password change endpoint (`POST /api/v1/auth/change-password` or similar).
2. Enter the test data.
3. Submit the request.

Expected Result:
- Password is successfully changed.
- System returns confirmation.
- User can log in with the new password.
- Old password no longer works.

#### TEST-050 — Password Change with Mismatch (Unhappy Path)
Role: Any User
Type: Unhappy Path (Validation)

Prerequisites:
- User logged in.

Test Data:
- Current Password: `currentPassword123`
- New Password: `newPassword@123`
- Confirm New Password: `differentPassword@123`

Steps:
1. Use the user token to access the password change endpoint.
2. Enter the test data.
3. Submit the request.

Expected Result:
- System rejects the request due to password mismatch.
- Appropriate error message is displayed.
- Password remains unchanged.