# EventQR Backend Endpoint Completion Report

## Summary
This report documents the current backend endpoint set for `EventQRBackend/eventqr` and the final-app endpoint coverage after the latest cleanup and stub replacement pass.

## Status Legend
- `ALREADY EXISTING` - endpoint already had working backend behavior before this pass.
- `IMPLEMENTED` - endpoint was missing or stubbed and now has working backend behavior.
- `INTENTIONALLY SIMULATED` - endpoint is implemented with a safe backend simulation because no external service is wired in this repo.
- `PARTIAL` - endpoint exists but has limited behavior.

## Endpoint Coverage

### 1. Authentication and Current User
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `POST /api/v1/auth/register` | ALREADY EXISTING | [AuthController.java](src/main/java/com/thedavelopers/eventqr/features/auth/controller/AuthController.java) | `UserService.create(...)` | Creates attendee accounts. | None |
| `POST /api/v1/auth/login` | ALREADY EXISTING | [AuthController.java](src/main/java/com/thedavelopers/eventqr/features/auth/controller/AuthController.java) | `AuthService.login(...)` | Returns JWT access token. | None |
| `POST /api/v1/auth/logout` | ALREADY EXISTING | [AuthController.java](src/main/java/com/thedavelopers/eventqr/features/auth/controller/AuthController.java) | N/A | Stateless logout acknowledgement. | None |
| `POST /api/v1/auth/forgot-password` | IMPLEMENTED | [AuthController.java](src/main/java/com/thedavelopers/eventqr/features/auth/controller/AuthController.java) | `PasswordResetService.requestReset(...)` | Generates a short-lived reset token and returns it in the response because no email provider is wired. | Email provider not wired; simulated token delivery |
| `POST /api/v1/auth/reset-password` | IMPLEMENTED | [AuthController.java](src/main/java/com/thedavelopers/eventqr/features/auth/controller/AuthController.java) | `PasswordResetService.resetPassword(...)` | Resets the stored password using the issued reset token. | Email provider not wired; uses in-memory reset token registry |
| `GET /api/v1/auth/me` | ALREADY EXISTING | [AuthController.java](src/main/java/com/thedavelopers/eventqr/features/auth/controller/AuthController.java) | `UserService.findOne(...)` | Returns authenticated user profile. | None |
| `PATCH /api/v1/auth/me/password` | ALREADY EXISTING | [AuthController.java](src/main/java/com/thedavelopers/eventqr/features/auth/controller/AuthController.java) | `UserService.changePassword(...)` | Changes the current password with the current password check. | None |
| `GET /api/v1/users/me` | ALREADY EXISTING | [UserController.java](src/main/java/com/thedavelopers/eventqr/features/users/controller/UserController.java) | `UserService.findOne(...)` | Current-user profile lookup. | None |
| `PATCH /api/v1/users/me` | ALREADY EXISTING | [UserController.java](src/main/java/com/thedavelopers/eventqr/features/users/controller/UserController.java) | `UserService.updateProfile(...)` | Updates full name and phone number. | None |
| `POST /api/v1/users/me/avatar` | IMPLEMENTED | [UserController.java](src/main/java/com/thedavelopers/eventqr/features/users/controller/UserController.java) | `FileStorageService.store(...)` | Stores the uploaded avatar in the backend file registry; it is not persisted to a user-profile column because the schema does not currently have one. | File storage is simulated in-memory |

### 2. Admin / SuperAdmin Account Management
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `GET /api/v1/admin/users` | ALREADY EXISTING | [AdminController.java](src/main/java/com/thedavelopers/eventqr/features/admin/controller/AdminController.java) | `UserService.findAllUsers()` | Requires admin access. | None |
| `GET /api/v1/admin/users/{userId}` | ALREADY EXISTING | [AdminController.java](src/main/java/com/thedavelopers/eventqr/features/admin/controller/AdminController.java) | `UserService.findOne(...)` | Requires admin access. | None |
| `POST /api/v1/admin/users/admins` | ALREADY EXISTING | [AdminController.java](src/main/java/com/thedavelopers/eventqr/features/admin/controller/AdminController.java) | `UserService.create(...)` | Creates admin accounts. | None |
| `PATCH /api/v1/admin/users/{userId}` | ALREADY EXISTING | [AdminController.java](src/main/java/com/thedavelopers/eventqr/features/admin/controller/AdminController.java) | `UserService.updateProfile(...)` | Requires admin access. | None |
| `PATCH /api/v1/admin/users/{userId}/status` | ALREADY EXISTING | [AdminController.java](src/main/java/com/thedavelopers/eventqr/features/admin/controller/AdminController.java) | `UserService.updateStatus(...)` | Requires admin access. | None |
| `PATCH /api/v1/admin/users/{userId}/roles` | ALREADY EXISTING | [AdminController.java](src/main/java/com/thedavelopers/eventqr/features/admin/controller/AdminController.java) | `UserService.changeRoleResponse(...)` | Requires admin access. | None |
| `DELETE /api/v1/admin/users/{userId}` | ALREADY EXISTING | [AdminController.java](src/main/java/com/thedavelopers/eventqr/features/admin/controller/AdminController.java) | `UserService.softDelete(...)` | Soft-deletes by suspending the account. | None |

### 3. Events
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `GET /api/v1/events` | ALREADY EXISTING | [EventController.java](src/main/java/com/thedavelopers/eventqr/features/events/controller/EventController.java) | `EventService.findAllVisible()` | Public/available events. | None |
| `GET /api/v1/events/{eventId}` | ALREADY EXISTING | [EventController.java](src/main/java/com/thedavelopers/eventqr/features/events/controller/EventController.java) | `EventService.findOne(...)` | Public event detail lookup. | None |
| `GET /api/v1/events/{eventId}/availability` | ALREADY EXISTING | [EventController.java](src/main/java/com/thedavelopers/eventqr/features/events/controller/EventController.java) | `EventService.checkAvailability(...)` | Public registration/capacity check. | None |
| `GET /api/v1/organizer/events` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.listEvents(...)` | Organizer-managed event list. | None |
| `GET /api/v1/organizer/events/{eventId}` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.event(...)` | Organizer event detail. | None |
| `PATCH /api/v1/organizer/events/{eventId}` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.updateEvent(...)` | Organizer event update. | None |
| `PATCH /api/v1/organizer/events/{eventId}/status` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.updateStatus(...)` | Event status update. | None |

### 4. Event Creation Requests
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `POST /api/v1/event-requests` | ALREADY EXISTING | [EventRequestController.java](src/main/java/com/thedavelopers/eventqr/features/events/controller/EventRequestController.java) | `EventCreationRequestService.create(...)` | Attendee submits request. | None |
| `GET /api/v1/event-requests/me` | ALREADY EXISTING | [EventRequestController.java](src/main/java/com/thedavelopers/eventqr/features/events/controller/EventRequestController.java) | `EventCreationRequestService.findByRequester(...)` | Current-user request list. | None |
| `GET /api/v1/event-requests/{requestId}` | ALREADY EXISTING | [EventRequestController.java](src/main/java/com/thedavelopers/eventqr/features/events/controller/EventRequestController.java) | `EventCreationRequestService.findOne(...)` | Request detail lookup. | None |
| `GET /api/v1/admin/event-requests` | ALREADY EXISTING | [AdminController.java](src/main/java/com/thedavelopers/eventqr/features/admin/controller/AdminController.java) | `EventCreationRequestService.findAllForAdmin()` | Admin request list. | None |
| `GET /api/v1/admin/event-requests/{requestId}` | ALREADY EXISTING | [AdminController.java](src/main/java/com/thedavelopers/eventqr/features/admin/controller/AdminController.java) | `EventCreationRequestService.findOneForAdmin(...)` | Admin request review detail. | None |
| `PATCH /api/v1/admin/event-requests/{requestId}/approve` | ALREADY EXISTING | [AdminController.java](src/main/java/com/thedavelopers/eventqr/features/admin/controller/AdminController.java) | `EventCreationRequestService.approve(...)` | Approves request. | None |
| `PATCH /api/v1/admin/event-requests/{requestId}/reject` | ALREADY EXISTING | [AdminController.java](src/main/java/com/thedavelopers/eventqr/features/admin/controller/AdminController.java) | `EventCreationRequestService.reject(...)` | Rejects request. | None |
| `PATCH /api/v1/admin/event-requests/{requestId}/upgrade-organizer` | ALREADY EXISTING | [AdminController.java](src/main/java/com/thedavelopers/eventqr/features/admin/controller/AdminController.java) | `EventCreationRequestService.upgradeOrganizer(...)` | Upgrades attendee to organizer. | None |

### 5. Attendee Event Registration and QR Credential
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `POST /api/v1/events/{eventId}/registrations` | ALREADY EXISTING | [EventController.java](src/main/java/com/thedavelopers/eventqr/features/events/controller/EventController.java) | `RegistrationService.register(...)` | Attendee registration flow. | None |
| `GET /api/v1/registrations/me` | ALREADY EXISTING | [RegistrationController.java](src/main/java/com/thedavelopers/eventqr/features/registrations/controller/RegistrationController.java) | `RegistrationService.findByAttendeeUserId(...)` | Current attendee registrations. | None |
| `GET /api/v1/registrations/{registrationId}` | ALREADY EXISTING | [RegistrationController.java](src/main/java/com/thedavelopers/eventqr/features/registrations/controller/RegistrationController.java) | `RegistrationService.findOne(...)` | Registration detail. | None |
| `DELETE /api/v1/registrations/{registrationId}` | ALREADY EXISTING | [RegistrationController.java](src/main/java/com/thedavelopers/eventqr/features/registrations/controller/RegistrationController.java) | `RegistrationService.cancel(...)` | Registration cancellation. | None |
| `POST /api/v1/registrations/{registrationId}/qr` | ALREADY EXISTING | [RegistrationController.java](src/main/java/com/thedavelopers/eventqr/features/registrations/controller/RegistrationController.java) | `QrCredentialService.issueOrReturnExisting(...)` | QR issuance. | None |
| `POST /api/v1/registrations/{registrationId}/qr/link` | ALREADY EXISTING | [RegistrationController.java](src/main/java/com/thedavelopers/eventqr/features/registrations/controller/RegistrationController.java) | `QrCredentialService.issueOrReturnExisting(...)` | QR link/reissue behavior. | None |
| `GET /api/v1/registrations/{registrationId}/qr/one-time` | ALREADY EXISTING | [RegistrationController.java](src/main/java/com/thedavelopers/eventqr/features/registrations/controller/RegistrationController.java) | `QrCredentialService.generateOneTimeDisplay(...)` | One-time QR display. | None |
| `POST /api/v1/registrations/{registrationId}/qr/download` | ALREADY EXISTING | [RegistrationController.java](src/main/java/com/thedavelopers/eventqr/features/registrations/controller/RegistrationController.java) | `QrCredentialService.markDisplayed(...)` | QR download tracking. | None |
| `POST /api/v1/registrations/{registrationId}/qr/email` | ALREADY EXISTING | [RegistrationController.java](src/main/java/com/thedavelopers/eventqr/features/registrations/controller/RegistrationController.java) | `QrCredentialService.markEmailQueued(...)` | QR email queue tracking. | Email provider not wired; queue status persisted |
| `POST /api/v1/registrations/{registrationId}/qr/email/retry` | ALREADY EXISTING | [RegistrationController.java](src/main/java/com/thedavelopers/eventqr/features/registrations/controller/RegistrationController.java) | `QrCredentialService.markEmailQueued(...)` | Retry queue tracking. | Email provider not wired; queue status persisted |
| `GET /api/v1/registrations/{registrationId}/email-status` | ALREADY EXISTING | [RegistrationController.java](src/main/java/com/thedavelopers/eventqr/features/registrations/controller/RegistrationController.java) | `QrCredentialService.findById(...)` | QR email status lookup. | None |

### 6. Staff Assignment
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `GET /api/v1/organizer/events/{eventId}/staff` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.staff(...)` | Organizer staff list. | None |
| `POST /api/v1/organizer/events/{eventId}/staff` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.addStaff(...)` | Staff assignment. | None |
| `GET /api/v1/organizer/events/{eventId}/staff/search` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.searchUsers(...)` | Search candidate users. | None |
| `PATCH /api/v1/organizer/events/{eventId}/staff/{staffId}` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.updateStaff(...)` | Staff update. | None |
| `DELETE /api/v1/organizer/events/{eventId}/staff/{staffId}` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.removeStaff(...)` | Staff removal. | None |
| `GET /api/v1/staff/events` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `EventStaffAssignmentRepository.findByStaffUserId(...)` + `EventService.findOne(...)` | Staff event list. | None |
| `GET /api/v1/staff/events/{eventId}` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `EventService.findOne(...)` | Staff event view. | None |

### 7. ID Template and Printing
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `GET /api/v1/organizer/events/{eventId}/id-templates` | ALREADY EXISTING | [OrganizerIdTemplateController.java](src/main/java/com/thedavelopers/eventqr/features/idprinting/controller/OrganizerIdTemplateController.java) | `IdPrintingService.listTemplates(...)` | Template catalog. | None |
| `POST /api/v1/organizer/events/{eventId}/id-template` | ALREADY EXISTING | [OrganizerIdTemplateController.java](src/main/java/com/thedavelopers/eventqr/features/idprinting/controller/OrganizerIdTemplateController.java) | `IdPrintingService.saveTemplate(...)` | Template create/save. | None |
| `GET /api/v1/organizer/events/{eventId}/id-template` | ALREADY EXISTING | [OrganizerIdTemplateController.java](src/main/java/com/thedavelopers/eventqr/features/idprinting/controller/OrganizerIdTemplateController.java) | `IdPrintingService.getTemplate(...)` | Current configured template. | None |
| `PATCH /api/v1/organizer/events/{eventId}/id-template` | ALREADY EXISTING | [OrganizerIdTemplateController.java](src/main/java/com/thedavelopers/eventqr/features/idprinting/controller/OrganizerIdTemplateController.java) | `IdPrintingService.saveTemplate(...)` | Template update. | None |
| `POST /api/v1/organizer/events/{eventId}/id-template/logo` | IMPLEMENTED | [OrganizerIdTemplateController.java](src/main/java/com/thedavelopers/eventqr/features/idprinting/controller/OrganizerIdTemplateController.java) | `FileStorageService.store(...)` | Stores the uploaded logo in the backend file registry. | File storage is simulated in-memory |
| `GET /api/v1/organizer/events/{eventId}/id-template/preview` | ALREADY EXISTING | [OrganizerIdTemplateController.java](src/main/java/com/thedavelopers/eventqr/features/idprinting/controller/OrganizerIdTemplateController.java) | `IdPrintingService.getTemplate(...)` | Template preview. | None |
| `POST /api/v1/staff/events/{eventId}/attendees/{attendeeId}/id-preview` | ALREADY EXISTING | [OrganizerIdTemplateController.java](src/main/java/com/thedavelopers/eventqr/features/idprinting/controller/OrganizerIdTemplateController.java) | `IdPrintingService.previewForAttendee(...)` | ID preview generation. | None |
| `POST /api/v1/staff/events/{eventId}/attendees/{attendeeId}/print-id` | ALREADY EXISTING | [OrganizerIdTemplateController.java](src/main/java/com/thedavelopers/eventqr/features/idprinting/controller/OrganizerIdTemplateController.java) | `IdPrintingService.printForAttendee(...)` | Print job creation. | Printer integration not wired; backend returns generated print result |
| `POST /api/v1/staff/events/{eventId}/attendees/{attendeeId}/reprint-id` | ALREADY EXISTING | [OrganizerIdTemplateController.java](src/main/java/com/thedavelopers/eventqr/features/idprinting/controller/OrganizerIdTemplateController.java) | `IdPrintingService.printForAttendee(...)` | Reprint job creation. | Printer integration not wired; backend returns generated print result |

### 8. Scan Purposes and Transaction Rules
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `GET /api/v1/organizer/events/{eventId}/scan-purposes` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.scanPurposes(...)` | Organizer scan purpose list. | None |
| `POST /api/v1/organizer/events/{eventId}/scan-purposes` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.saveScanPurpose(...)` | Create scan purpose. | None |
| `PATCH /api/v1/organizer/events/{eventId}/scan-purposes/{purposeId}` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.saveScanPurpose(...)` | Update scan purpose. | None |
| `DELETE /api/v1/organizer/events/{eventId}/scan-purposes/{purposeId}` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.deleteScanPurpose(...)` | Delete scan purpose. | None |
| `PATCH /api/v1/organizer/events/{eventId}/scan-purposes/{purposeId}/enable` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.enableScanPurpose(...)` | Enable scan purpose. | None |
| `PATCH /api/v1/organizer/events/{eventId}/scan-purposes/{purposeId}/disable` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.enableScanPurpose(...)` | Disable scan purpose. | None |
| `PATCH /api/v1/organizer/events/{eventId}/scan-purposes/{purposeId}/tracking-only` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.toggleTrackingOnly(...)` | Tracking-only mode. | None |
| `GET /api/v1/organizer/events/{eventId}/transaction-rules` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.listTransactionRules(...)` | Transaction rules view. | None |
| `PUT /api/v1/organizer/events/{eventId}/transaction-rules` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.saveTransactionRule(...)` | Transaction rule update. | None |
| `GET /api/v1/staff/events/{eventId}/scan-purposes` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `ScanPurposeService.listByEventId(...)` | Staff-visible active scan purposes. | None |

### 9. QR Scanning and Verification
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `POST /api/v1/staff/events/{eventId}/scan/verify` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `TransactionService.verify(...)` | Verification without mutating state. | None |
| `POST /api/v1/staff/events/{eventId}/scan/entry` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `TransactionService.record(...)` | Entry scan transaction. | None |
| `POST /api/v1/staff/events/{eventId}/scan/attendance` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `TransactionService.record(...)` | Attendance scan transaction. | None |
| `POST /api/v1/staff/events/{eventId}/scan/benefit-claim` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `TransactionService.record(...)` | Benefit claim scan transaction. | None |
| `POST /api/v1/staff/events/{eventId}/scan/booth-visit` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `TransactionService.record(...)` | Booth visit scan transaction. | None |
| `POST /api/v1/staff/events/{eventId}/scan/reward-redemption` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `TransactionService.record(...)` | Reward redemption scan transaction. | None |
| `POST /api/v1/staff/events/{eventId}/scan/exit` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `TransactionService.record(...)` | Exit scan transaction. | None |
| `POST /api/v1/staff/events/{eventId}/scan/reject` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `TransactionService.record(...)` | Rejection transaction. | None |
| `GET /api/v1/staff/events/{eventId}/scan/latest` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `TransactionService.latest(...)` | Latest scan lookup. | None |

### 10. Attendee Records and Status
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `GET /api/v1/organizer/events/{eventId}/attendees` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.attendees(...)` | Organizer attendee list. | None |
| `GET /api/v1/organizer/events/{eventId}/attendees/{attendeeId}` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.attendee(...)` | Organizer attendee detail. | None |
| `GET /api/v1/organizer/events/{eventId}/attendees/search` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.searchAttendees(...)` | Attendee search. | None |
| `PATCH /api/v1/organizer/events/{eventId}/attendees/{attendeeId}/status` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.updateAttendeeStatus(...)` | Organizer attendee status update. | None |
| `GET /api/v1/staff/events/{eventId}/attendees/{attendeeId}` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `RegistrationService.findByAttendeeUserId(...)` | Staff attendee lookup within event. | None |
| `GET /api/v1/attendees/me/events/{eventId}/status` | ALREADY EXISTING | [AttendeeController.java](src/main/java/com/thedavelopers/eventqr/features/attendees/controller/AttendeeController.java) | `RegistrationService.findByEventAndAttendee(...)` | Current attendee event status. | None |

### 11. Transactions and Logs
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `GET /api/v1/organizer/events/{eventId}/transactions` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.transactions(...)` | Organizer transaction feed. | None |
| `GET /api/v1/organizer/events/{eventId}/transactions/{transactionId}` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.transaction(...)` | Organizer transaction detail. | None |
| `GET /api/v1/staff/events/{eventId}/transactions` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `TransactionService.findByEvent(...)` | Staff event transaction feed. | None |
| `GET /api/v1/attendees/me/transactions` | ALREADY EXISTING | [AttendeeController.java](src/main/java/com/thedavelopers/eventqr/features/attendees/controller/AttendeeController.java) | `TransactionService.findByAttendee(...)` | Current attendee transaction feed. | None |
| `GET /api/v1/attendees/me/events/{eventId}/transactions` | ALREADY EXISTING | [AttendeeController.java](src/main/java/com/thedavelopers/eventqr/features/attendees/controller/AttendeeController.java) | `TransactionService.findByEventAndAttendee(...)` | Current attendee event transaction feed. | None |
| `POST /api/v1/transactions/{transactionId}/notes` | ALREADY EXISTING | [TransactionController.java](src/main/java/com/thedavelopers/eventqr/features/transactions/controller/TransactionController.java) | `TransactionService.updateNotes(...)` or existing log flow | Transaction note support is already routed by the transaction feature. | None |

### 12. Attendance Monitoring
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `GET /api/v1/organizer/events/{eventId}/attendance` | ALREADY EXISTING | [OrganizerAttendanceController.java](src/main/java/com/thedavelopers/eventqr/features/reports/controller/OrganizerAttendanceController.java) | `OrganizerAttendanceService.summary(...)` | Attendance summary. | None |
| `GET /api/v1/organizer/events/{eventId}/attendance/records` | ALREADY EXISTING | [OrganizerAttendanceController.java](src/main/java/com/thedavelopers/eventqr/features/reports/controller/OrganizerAttendanceController.java) | `OrganizerAttendanceService.records(...)` | Attendance records. | None |
| `GET /api/v1/organizer/events/{eventId}/attendance/no-shows` | ALREADY EXISTING | [OrganizerAttendanceController.java](src/main/java/com/thedavelopers/eventqr/features/reports/controller/OrganizerAttendanceController.java) | `OrganizerAttendanceService.noShows(...)` | No-show list. | None |
| `GET /api/v1/organizer/events/{eventId}/attendance/recent-checkins` | ALREADY EXISTING | [OrganizerAttendanceController.java](src/main/java/com/thedavelopers/eventqr/features/reports/controller/OrganizerAttendanceController.java) | `OrganizerAttendanceService.recentCheckins(...)` | Recent check-ins. | None |

### 13. Benefits / Claims
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `GET /api/v1/organizer/events/{eventId}/benefits` | IMPLEMENTED | [OrganizerBenefitController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/OrganizerBenefitController.java) | `OrganizerBenefitService.list(...)` | Backed by `EventBenefit` JPA entity. | None |
| `POST /api/v1/organizer/events/{eventId}/benefits` | IMPLEMENTED | [OrganizerBenefitController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/OrganizerBenefitController.java) | `OrganizerBenefitService.create(...)` | Creates or updates the benefit catalog. | None |
| `PATCH /api/v1/organizer/events/{eventId}/benefits/{benefitId}` | IMPLEMENTED | [OrganizerBenefitController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/OrganizerBenefitController.java) | `OrganizerBenefitService.update(...)` | Updates benefit metadata. | None |
| `DELETE /api/v1/organizer/events/{eventId}/benefits/{benefitId}` | IMPLEMENTED | [OrganizerBenefitController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/OrganizerBenefitController.java) | `OrganizerBenefitService.delete(...)` | Deletes a benefit. | None |
| `GET /api/v1/organizer/events/{eventId}/benefit-claims` | IMPLEMENTED | [OrganizerBenefitController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/OrganizerBenefitController.java) | `OrganizerBenefitService.claims(...)` | Returns benefit-claim transactions. | None |
| `GET /api/v1/attendees/me/events/{eventId}/benefit-claims` | ALREADY EXISTING | [AttendeeController.java](src/main/java/com/thedavelopers/eventqr/features/attendees/controller/AttendeeController.java) | `TransactionService.findByEventAndAttendee(...)` + benefit-claim filter | Current attendee benefit claims. | None |

### 14. Rewards
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `GET /api/v1/events/{eventId}/rewards` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.findRewards(...)` | Public rewards list. | None |
| `GET /api/v1/events/{eventId}/rewards/{rewardId}` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.findReward(...)` | Public reward detail. | None |
| `GET /api/v1/organizer/events/{eventId}/reward-settings` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.event(...)` | Uses event reward status. | None |
| `PATCH /api/v1/organizer/events/{eventId}/reward-settings` | ALREADY EXISTING | [OrganizerController.java](src/main/java/com/thedavelopers/eventqr/features/organizer/controller/OrganizerController.java) | `OrganizerService.updateRewardSettings(...)` | Reward settings update. | None |
| `GET /api/v1/organizer/events/{eventId}/rewards` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.findRewards(...)` | Organizer reward list. | None |
| `POST /api/v1/organizer/events/{eventId}/rewards` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.saveReward(...)` | Reward create. | None |
| `PATCH /api/v1/organizer/events/{eventId}/rewards/{rewardId}` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.updateReward(...)` | Reward update. | None |
| `DELETE /api/v1/organizer/events/{eventId}/rewards/{rewardId}` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.deleteReward(...)` | Reward delete. | None |
| `POST /api/v1/staff/events/{eventId}/rewards/{rewardId}/redeem` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `RewardService.redeem(...)` | Staff redemption flow. | None |
| `GET /api/v1/attendees/me/events/{eventId}/claimed-rewards` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.findRedemptions(...)` | Current attendee claimed rewards. | None |
| `GET /api/v1/organizer/events/{eventId}/claimed-rewards` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.findRedemptions(...)` | Organizer claimed-rewards view. | None |

### 15. Points
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `GET /api/v1/organizer/events/{eventId}/point-rules` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.listPointRules(...)` | Point rules list. | None |
| `POST /api/v1/organizer/events/{eventId}/point-rules` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.savePointRule(...)` | Point rule create. | None |
| `PATCH /api/v1/organizer/events/{eventId}/point-rules/{ruleId}` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.updatePointRule(...)` | Point rule update. | None |
| `DELETE /api/v1/organizer/events/{eventId}/point-rules/{ruleId}` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.deletePointRule(...)` | Point rule delete. | None |
| `POST /api/v1/staff/events/{eventId}/points/assign` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `RewardService.assignPoints(...)` | Manual point assignment. | None |
| `POST /api/v1/staff/events/{eventId}/points/deduct` | ALREADY EXISTING | [StaffController.java](src/main/java/com/thedavelopers/eventqr/features/staff/controller/StaffController.java) | `RewardService.deductPoints(...)` | Manual point deduction. | None |
| `GET /api/v1/attendees/me/events/{eventId}/points` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.getBalance(...)` | Current attendee points. | None |
| `GET /api/v1/attendees/me/events/{eventId}/point-transactions` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.findPointTransactions(...)` | Current attendee point ledger. | None |
| `GET /api/v1/organizer/events/{eventId}/point-transactions` | ALREADY EXISTING | [RewardRoutesController.java](src/main/java/com/thedavelopers/eventqr/features/rewards/controller/RewardRoutesController.java) | `RewardService.findPointTransactions(...)` | Organizer point ledger. | None |

### 16. Reports and Analytics
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `GET /api/v1/organizer/events/{eventId}/reports/summary` | ALREADY EXISTING | [OrganizerReportController.java](src/main/java/com/thedavelopers/eventqr/features/reports/controller/OrganizerReportController.java) | `OrganizerService.report(...)` / `ReportService` | Summary report. | None |
| `GET /api/v1/organizer/events/{eventId}/reports/attendance` | ALREADY EXISTING | [OrganizerReportController.java](src/main/java/com/thedavelopers/eventqr/features/reports/controller/OrganizerReportController.java) | `ReportService` | Attendance report. | None |
| `GET /api/v1/organizer/events/{eventId}/reports/entries` | ALREADY EXISTING | [OrganizerReportController.java](src/main/java/com/thedavelopers/eventqr/features/reports/controller/OrganizerReportController.java) | `ReportService` | Entry report. | None |
| `GET /api/v1/organizer/events/{eventId}/reports/exits` | ALREADY EXISTING | [OrganizerReportController.java](src/main/java/com/thedavelopers/eventqr/features/reports/controller/OrganizerReportController.java) | `ReportService` | Exit report. | None |
| `GET /api/v1/organizer/events/{eventId}/reports/claims` | ALREADY EXISTING | [OrganizerReportController.java](src/main/java/com/thedavelopers/eventqr/features/reports/controller/OrganizerReportController.java) | `ReportService` | Claim report. | None |
| `GET /api/v1/organizer/events/{eventId}/reports/booth-visits` | ALREADY EXISTING | [OrganizerReportController.java](src/main/java/com/thedavelopers/eventqr/features/reports/controller/OrganizerReportController.java) | `ReportService` | Booth visit report. | None |
| `GET /api/v1/organizer/events/{eventId}/reports/rewards` | ALREADY EXISTING | [OrganizerReportController.java](src/main/java/com/thedavelopers/eventqr/features/reports/controller/OrganizerReportController.java) | `ReportService` | Reward report. | None |
| `GET /api/v1/organizer/events/{eventId}/reports/points` | ALREADY EXISTING | [OrganizerReportController.java](src/main/java/com/thedavelopers/eventqr/features/reports/controller/OrganizerReportController.java) | `ReportService` | Points report. | None |
| `POST /api/v1/organizer/events/{eventId}/reports/export` | ALREADY EXISTING | [OrganizerReportController.java](src/main/java/com/thedavelopers/eventqr/features/reports/controller/OrganizerReportController.java) | `ReportService.export(...)` | Export flow. | None |

### 17. Notifications
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `GET /api/v1/notifications` | ALREADY EXISTING | [NotificationController.java](src/main/java/com/thedavelopers/eventqr/features/notifications/controller/NotificationController.java) | `NotificationService.findAll(...)` | Current-user notifications. | None |
| `GET /api/v1/notifications/{notificationId}` | ALREADY EXISTING | [NotificationController.java](src/main/java/com/thedavelopers/eventqr/features/notifications/controller/NotificationController.java) | `NotificationService.findOne(...)` | Notification detail. | None |
| `PATCH /api/v1/notifications/{notificationId}/read` | ALREADY EXISTING | [NotificationController.java](src/main/java/com/thedavelopers/eventqr/features/notifications/controller/NotificationController.java) | `NotificationService.markRead(...)` | Read flag update. | None |
| `PATCH /api/v1/notifications/read-all` | ALREADY EXISTING | [NotificationController.java](src/main/java/com/thedavelopers/eventqr/features/notifications/controller/NotificationController.java) | `NotificationService.markAllRead(...)` | Bulk read update. | None |
| `POST /api/v1/notifications` | ALREADY EXISTING | [NotificationController.java](src/main/java/com/thedavelopers/eventqr/features/notifications/controller/NotificationController.java) | `NotificationService.create(...)` | Notification create. | None |
| `DELETE /api/v1/notifications/{notificationId}` | ALREADY EXISTING | [NotificationController.java](src/main/java/com/thedavelopers/eventqr/features/notifications/controller/NotificationController.java) | `NotificationService.delete(...)` | Notification delete. | None |

### 18. Audit Logs
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `GET /api/v1/admin/audit-logs` | ALREADY EXISTING | [AuditLogController.java](src/main/java/com/thedavelopers/eventqr/features/audit/controller/AuditLogController.java) | `AuditLogService.findAll()` | Canonical admin audit-log endpoint. | None |
| `GET /api/v1/organizer/events/{eventId}/audit-logs` | ALREADY EXISTING | [AuditLogController.java](src/main/java/com/thedavelopers/eventqr/features/audit/controller/AuditLogController.java) | `AuditLogService.findByEvent(...)` | Canonical organizer audit-log endpoint. | None |
| `POST /api/v1/audit-logs` | ALREADY EXISTING | [AuditLogController.java](src/main/java/com/thedavelopers/eventqr/features/audit/controller/AuditLogController.java) | `AuditLogService.log(...)` | Audit-log create endpoint. | None |

### 19. File / Upload Support
| Endpoint | Status | Controller file | Service method | Notes | External dependency |
|---|---|---|---|---|---|
| `POST /api/v1/uploads/event-logo` | IMPLEMENTED | [UploadController.java](src/main/java/com/thedavelopers/eventqr/features/uploads/controller/UploadController.java) | `FileStorageService.store(...)` | Stores event logo in backend file registry. | File storage is simulated in-memory |
| `POST /api/v1/uploads/id-template-assets` | IMPLEMENTED | [UploadController.java](src/main/java/com/thedavelopers/eventqr/features/uploads/controller/UploadController.java) | `FileStorageService.store(...)` | Stores ID template asset in backend file registry. | File storage is simulated in-memory |
| `POST /api/v1/uploads/profile-photo` | IMPLEMENTED | [UploadController.java](src/main/java/com/thedavelopers/eventqr/features/uploads/controller/UploadController.java) | `FileStorageService.store(...)` | Stores profile photo in backend file registry. | File storage is simulated in-memory |
| `GET /api/v1/files/{fileId}` | IMPLEMENTED | [UploadController.java](src/main/java/com/thedavelopers/eventqr/features/uploads/controller/UploadController.java) | `FileStorageService.find(...)` | Returns stored file metadata from the backend registry. | File storage is simulated in-memory |
| `DELETE /api/v1/files/{fileId}` | IMPLEMENTED | [UploadController.java](src/main/java/com/thedavelopers/eventqr/features/uploads/controller/UploadController.java) | `FileStorageService.delete(...)` | Deletes the stored file from the backend registry. | File storage is simulated in-memory |

## Duplicate Mapping Audit Result
- A full controller scan across `src/main/java/**/controller/*.java` reported: `No duplicate HTTP method+path routes detected.`
- Confirmed canonical owners:
  - `GET /api/v1/admin/audit-logs` exists once in `AuditLogController`.
  - `GET /api/v1/organizer/events/{eventId}/audit-logs` exists once in `AuditLogController`.

## Verification
- `./mvnw.cmd clean test` - passed.
- `./mvnw.cmd spring-boot:run` - reached application startup and Tomcat initialization; the local environment then failed on database connectivity because no PostgreSQL server is available at `localhost:5432`.

## Remaining Risks
- File upload, avatar upload, and password reset are backend-safe simulations backed by in-memory state, so they will not survive process restarts.
- Printer integration is still simulated through backend print-result generation.
- Real email delivery and external blob storage are not wired in this repository.
- Render deployment still requires a valid database connection environment; the code now boots past controller registration and duplicate mapping checks.
