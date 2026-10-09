# EventQR

EventQR is a QR-based event management platform built for attendee registration, event check-in, organizer coordination, staff scanning, reward tracking, and administrative oversight. The repository contains a Java Spring Boot backend and an Android mobile client that work together to support event operations across multiple user roles.

## Project Status

This is an active project repository for a campus/community event system. It is intended for local development, demonstration, and further feature expansion, not as a production-ready deployment package without environment-specific hardening and configuration.

## Core Idea

The platform allows:

- attendees to discover events, register, view QR credentials, and track rewards
- staff to validate attendee QR codes and perform event scans for entry, exit, attendance, booth visits, benefit claims, and reward redemption
- organizers to manage events, staff assignments, scan purposes, rewards, point rules, reports, and attendee details
- admins to oversee users, platform notifications, event requests, and audit activity

## Tech Stack

### Backend

- Java 21
- Spring Boot 3.5.x, Maven
- PostgreSQL (Supabase) with Flyway migrations
- Spring Security with JWT access and refresh tokens
- Spring Data JPA, Caffeine caching, Actuator health checks
- Brevo transactional email, PDFBox for ID printing, ZXing for QR generation
- Uploads stored in Postgres or an S3-compatible bucket
- Docker, deployed on Render

### Mobile App

- Kotlin, Android (minSdk 26, targetSdk 35)
- Jetpack Compose + Material 3
- Retrofit + OkHttp + Gson for backend calls
- ZXing for QR codes, uCrop for image cropping
- Encrypted session storage (AndroidX Security Crypto)
- Splash screen API and a generated Baseline Profile for fast cold start

## Repository Structure

```txt
EventQR/
├── .github/workflows/           # CI, dependency scan, DB backup, Supabase keepalive
├── EventQRBackend/eventqr/      # Spring Boot backend
│   ├── src/main/java/com/thedavelopers/eventqr/
│   │   ├── features/            # admin, attendance, auditlogs, auth, dashboard,
│   │   │                        # eventrequests, events, idprinting, notifications,
│   │   │                        # organizer, qrcredentials, qremail, registrations,
│   │   │                        # reports, rewards, scanning, staff, transactions,
│   │   │                        # uploads, users
│   │   └── shared/              # config, security, utils, exceptions
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── application-prod.properties
│   │   ├── logback-spring.xml
│   │   └── db/migration/        # Flyway migrations (V1 ... V38)
│   ├── src/test/                # Backend tests
│   ├── .env.example             # All supported environment variables
│   ├── Dockerfile
│   └── pom.xml
├── EventQRMobile/               # Kotlin Android client
│   ├── app/src/main/java/com/thedavelopers/eventqr/
│   │   ├── core/                # api (Retrofit), navigation, session, util
│   │   ├── features/            # admin, attendee, audit, auth, common, dashboard,
│   │   │                        # events, idprinting, landing, notifications,
│   │   │                        # organizer, qrcredential, registrations, reports,
│   │   │                        # rewards, scanpurposes, staff, transactions,
│   │   │                        # uploads, users
│   │   └── ui/                  # shared components and theme
│   ├── app/src/test, androidTest, benchmark
│   ├── baselineprofile/         # Baseline Profile generator module
│   └── build.gradle.kts
├── .gitignore
└── README.md
```

## Main Functional Areas

### Attendee features

- browse and view events
- register for events
- receive QR credentials
- view attendance and registration status
- view transactions and event participation history
- claim and redeem rewards
- view notifications and profile data

### Staff features

- select assigned events
- validate attendee QR codes
- perform event scans by purpose
- record attendance, exits, claims, and reward redemptions
- review scan history and transaction details
- access event attendee summaries for assigned events

### Organizer features

- request and manage events
- review attendee registrations
- create and manage staff assignments
- configure scan purposes
- manage rewards, point rules, and event-specific logic
- review transaction logs, event reports, attendance summaries, and audit activity

### Admin features

- manage users and accounts
- review event creation requests
- oversee audit logs and platform activity
- manage notifications and platform-level administrative operations

## Backend Architecture

The backend is organized as a feature-based Spring application under `com.thedavelopers.eventqr.features`. It includes controllers for auth, users, events, event requests, registrations, QR credentials, scanning, staff, organizer tools, rewards, transactions, reports, audit logs, ID printing, notifications, uploads, and admin operations.

The project uses:

- REST controllers under `/api/v1/...`
- Spring Security with JWT access and refresh tokens
- Flyway migration scripts for schema evolution
- PostgreSQL-backed persistence
- role-aware access across attendee, staff, organizer, and admin flows
- transactional check-in handling to keep concurrent scans consistent

Examples of implemented API groups include:

- `/api/v1/auth`
- `/api/v1/events`
- `/api/v1/registrations`
- `/api/v1/staff`
- `/api/v1/organizer`
- `/api/v1/admin`
- `/api/v1/rewards`
- `/api/v1/reports`
- `/api/v1/notifications`
- `/api/v1/health`

## Mobile App Architecture

The Android application is built with Jetpack Compose and organized into feature packages on top of a shared `core` layer (API client, navigation, session) and a `ui` layer (components, theme). The app includes:

- landing, login, registration, password reset, and profile screens
- role-based dashboards for attendees, staff, organizers, and admins
- event browsing, registration, and QR credential display
- attendee rewards, transactions, and notifications
- staff scanning workflows (camera permission is requested only when the scanner opens) and transaction logs
- organizer and admin screens for events, scan purposes, reports, ID printing, users, and audit logs
- encrypted session storage with background token refresh

The backend base URL comes from the `EVENTQR_BASE_URL` Gradle property and falls back to the deployed backend. To point the app at a local backend, set it in `EventQRMobile/gradle.properties` or on the command line:

```bash
./gradlew assembleDebug -PEVENTQR_BASE_URL=http://10.0.2.2:10000/api/v1/
```

## Local Development Setup

### Prerequisites

- JDK 21
- Maven or Maven wrapper
- PostgreSQL database
- Android Studio
- Android SDK configured for the project

### Backend

From the project root:

```bash
cd EventQRBackend/eventqr
./mvnw spring-boot:run
```

On Windows:

```bat
cd EventQRBackend\eventqr
mvnw.cmd spring-boot:run
```

Run the tests with `./mvnw verify`.

### Docker (optional)

```bash
cd EventQRBackend/eventqr
docker build -t eventqr-backend .
docker run --rm -p 10000:10000 eventqr-backend
```

### Android app

```bash
cd EventQRMobile
./gradlew assembleDebug
```

On Windows:

```bat
cd EventQRMobile
gradlew.bat assembleDebug
```

Then open `EventQRMobile` in Android Studio and run it on an emulator or physical device. Unit tests: `./gradlew testDebugUnitTest`.

## Required Configuration

The backend currently reads its configuration from environment variables (see `.env.example` for the full list) with defaults in `application.properties` and `application-prod.properties`.

Typical configuration includes:

- database host, port, and database name
- database username and password
- JWT secret and token lifetimes
- Brevo API key and sender address
- file storage type (`db` or `s3`) and bucket settings
- backend server port
- mobile app API base URL

Example environment variables used by the backend:

```bash
DB_URL=jdbc:postgresql://localhost:5432/eventqr
DB_USERNAME=postgres
DB_PASSWORD=postgres
JWT_SECRET=your_jwt_secret_here
JWT_EXPIRATION_MS=3600000
BREVO_API_KEY=your_brevo_key
BREVO_SENDER_EMAIL=noreply@example.com
PORT=10000
```

Do not commit real secrets, production URLs, or private deployment values to version control.

## Database migrations (Flyway)

- Migrations live in `EventQRBackend/eventqr/src/main/resources/db/migration` (currently V1 to V38). On a fresh database Flyway applies them in order; Hibernate's `ddl-auto=validate` only verifies the entity mapping against the migrated schema.
- Never change `spring.jpa.hibernate.ddl-auto` back to `update` or `create` on any environment that Flyway has already migrated — the two approaches fight over schema ownership and Flyway checksums drift.
- `spring.flyway.baseline-on-migrate=true` is only meant for pre-existing non-Flyway databases; a clean deploy does not rely on it.

## Deployment & operations

The backend runs on Render (Docker, `EventQRBackend/eventqr/Dockerfile`) against a Supabase Postgres database.

### Render environment

- Every variable the backend reads is listed with a placeholder in `EventQRBackend/eventqr/.env.example`. Copy it to `.env` for local use; `.env` is git-ignored.
- `SPRING_PROFILES_ACTIVE=prod` is required in production (small connection pool, graceful shutdown, virtual threads).
- `DB_URL` must be the Supabase **session pooler** URL with SSL, e.g. `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require`, with `DB_USERNAME=postgres.<project_ref>`.
- JVM memory flags come from `JAVA_TOOL_OPTIONS` in the Dockerfile; set the variable on Render to override them.
- Set Render's **Health Check Path** to `/api/v1/health` (returns 503 when the database is unreachable).

### GitHub secrets

| Secret | Used by |
|---|---|
| `SUPABASE_ANON_KEY` | `supabase-keepalive.yml` (keeps the free-tier project from pausing) |
| `SUPABASE_DB_URL` | `db-backup.yml`: `postgresql://` session pooler URL with `?sslmode=require` |
| `BACKUP_PASSPHRASE` | `db-backup.yml`: encrypts the dump. Generate with `openssl rand -base64 48` and keep a copy outside GitHub (password manager) |
| `NVD_API_KEY` | `dependency-check.yml`: weekly OWASP dependency scan |

### Backups and restore

`db-backup.yml` runs daily (and on demand from the Actions tab). It `pg_dump`s the `public` schema, encrypts it with GPG, and keeps it as a workflow artifact for 7 days.

> The repository is public, so any signed-in GitHub user can download these artifacts. The GPG passphrase is the only protection for the data: it must be high-entropy (`openssl rand -base64 48`), never a human-chosen password, and must be stored outside GitHub — secrets cannot be read back, so losing it makes every backup unrecoverable. Download backups you want to keep longer than 7 days.

To restore (Postgres 17 client tools):

```bash
gpg -d -o eventqr.dump eventqr-<timestamp>.dump.gpg     # prompts for BACKUP_PASSPHRASE
pg_restore --clean --if-exists --no-owner -d '<target postgres:// url>' eventqr.dump
```

Restore into a scratch database first; `--clean` replaces existing data in `public`.

### Rollback

- Application: in the Render dashboard, open the service's **Events** and use **Rollback** on the last good deploy.
- Schema: Flyway migrations are forward-only. A rolled-back app still sees the newer schema, so undo a schema change with a new migration (`V<next>__revert_<change>.sql`) rather than editing or deleting an applied one.

## Security Notes

This repository should not expose:

- database credentials
- production hostnames or environment values
- JWT secrets
- API tokens
- private service credentials
- local keystore files
- real user or attendee data

## Development Guidelines

- keep backend logic on the backend
- keep API contracts consistent between the Android app and the Spring service layer
- prefer feature-based organization for new work
- validate changes with the relevant backend build/test steps before merging
- keep environment-specific configuration out of source control

## License

No license has been specified for this repository yet. Add a license file before distributing or reusing the code publicly.

## Notes

This repository represents a full-stack event management system with QR-driven validation, role-based workflows, and both organizer/admin supervision and attendee-facing interactions. The project is structured for iterative development and is best approached as a connected backend + mobile application rather than as individual isolated modules.
