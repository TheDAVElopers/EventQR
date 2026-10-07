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
- Spring Boot 3.5.x
- Maven
- PostgreSQL
- Spring Security
- JWT authentication
- Flyway database migrations
- Spring Data JPA
- Docker-ready project structure

### Mobile App

- Kotlin
- Android SDK
- Android Compose + Material 3
- Retrofit + OkHttp for backend calls
- ZXing for QR-related functionality
- Session-aware app flow with role-based screens

## Repository Structure

```txt
EventQR/
├── .serena/                     # Serena AI configuration
├── EventQRBackend/              # Java Spring Boot backend
│   └── eventqr/
│       ├── src/
│       │   ├── main/
│       │   │   ├── java/com/thedavelopers/eventqr/
│       │   │   │   ├── features/
│       │   │   │   │   ├── admin/
│       │   │   │   │   │   ├── controller/
│       │   │   │   │   │   ├── service/
│       │   │   │   │   │   ├── repository/
│       │   │   │   │   │   ├── model/
│       │   │   │   │   │   └── dto/
│       │   │   │   │   ├── attendance/
│       │   │   │   │   │   ├── controller/
│       │   │   │   │   │   ├── service/
│       │   │   │   │   │   ├── repository/
│       │   │   │   │   │   ├── model/
│       │   │   │   │   │   └── dto/
│       │   │   │   │   ├── auth/
│       │   │   │   │   │   ├── controller/
│       │   │   │   │   │   ├── service/
│       │   │   │   │   │   ├── repository/
│       │   │   │   │   │   ├── model/
│       │   │   │   │   │   └── dto/
│       │   │   │   │   ├── events/
│       │   │   │   │   │   ├── controller/
│       │   │   │   │   │   ├── service/
│       │   │   │   │   │   ├── repository/
│       │   │   │   │   │   ├── model/
│       │   │   │   │   │   └── dto/
│       │   │   │   │   ├── organizer/
│       │   │   │   │   │   ├── controller/
│       │   │   │   │   │   ├── service/
│       │   │   │   │   │   ├── repository/
│       │   │   │   │   │   ├── model/
│       │   │   │   │   │   └── dto/
│       │   │   │   │   ├── staff/
│       │   │   │   │   │   ├── controller/
│       │   │   │   │   │   ├── service/
│       │   │   │   │   │   ├── repository/
│       │   │   │   │   │   ├── model/
│       │   │   │   │   │   └── dto/
│       │   │   │   │   ├── notifications/
│       │   │   │   │   │   ├── controller/
│       │   │   │   │   │   ├── service/
│       │   │   │   │   │   ├── repository/
│       │   │   │   │   │   ├── model/
│       │   │   │   │   │   └── dto/
│       │   │   │   │   ├── qremail/
│       │   │   │   │   │   ├── service/
│       │   │   │   │   │   │   ├── EmailGatewayService.java
│       │   │   │   │   │   │   ├── EmailTemplateBuilder.java
│       │   │   │   │   │   │   └── QREmailService.java
│       │   │   │   │   └── shared/
│       │   │   │   │       ├── config/
│       │   │   │   │       ├── security/
│       │   │   │   │       ├── utils/
│       │   │   │   │       └── exceptions/
│       │   │   │   └── resources/
│       │   │   │       ├── application.yml
│       │   │   │       ├── application-dev.yml
│       │   │   │       ├── schema.sql
│       │   │   │       └── data.sql
│       │   │   └── test/
│       │   │       └── java/com/thedavelopers/eventqr/
│       │   │           └── features/
│       ├── pom.xml
│       └── Dockerfile
├── EventQRMobile/               # Kotlin Android mobile client
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/thedavelopers/eventqr/
│   │   │   │   │   ├── features/
│   │   │   │   │   │   ├── auth/
│   │   │   │   │   │   ├── attendance/
│   │   │   │   │   │   ├── dashboard/
│   │   │   │   │   │   ├── events/
│   │   │   │   │   │   ├── organizers/
│   │   │   │   │   │   ├── profile/
│   │   │   │   │   │   ├── rewards/
│   │   │   │   │   │   ├── staff/
│   │   │   │   │   │   └── shared/
│   │   │   │   │   └── res/
│   │   │   │   │       ├── layout/
│   │   │   │   │       ├── menu/
│   │   │   │   │       ├── values/
│   │   │   │   │       ├── drawable/
│   │   │   │   │       └── navigation/
│   │   │   │   └── test/
│   │   │   │       └── java/com/thedavelopers/eventqr/
│   │   │   │           └── features/
│   ├── build.gradle
│   └── proguard-rules.pro
├── supabase/                    # Supabase configuration and migrations
│   ├── migrations/
│   │   ├── supabase/
│   │   └── ...
│   └── ...
├── .gitignore
├── AGENTS.md                    # Opencode agent configuration
├── FinalFlow.md                 # Project workflow documentation
├── opencode.json                # Opencode configuration
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

The backend is organized as a feature-based Spring application under `com.thedavelopers.eventqr.features`. It includes controllers for auth, users, events, registrations, staff, organizer tools, rewards, reports, notifications, uploads, and admin operations.

The project uses:

- REST controllers under `/api/v1/...`
- Spring Security with JWT-based auth
- Flyway migration scripts for schema evolution
- PostgreSQL-backed persistence
- role-aware access across attendee, staff, organizer, and admin flows

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

## Mobile App Architecture

The Android application is structured around feature packages and a shared core layer. The app currently includes:

- auth flows for login, registration, password reset, and profile changes
- dashboard screens based on user role
- event browsing and registration flows
- attendee reward and transaction views
- staff scanning workflows and transaction logs
- organizer/admin screens for overview and management
- API integration through a centralized Retrofit client and session handling

The current mobile app is configured to communicate with the deployed backend via a default base URL in `ApiConfig.kt`:

```kotlin
object ApiConfig {
    const val BASE_URL = "https://eventqr-backend-owoa.onrender.com/api/v1/"
}
```

If you are running the backend locally, update this value to your local API address before testing the app.

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

Then open the project in Android Studio and run it on an emulator or physical device.

## Required Configuration

The backend currently relies on environment variables and default fallbacks defined in `application.properties`.

Typical configuration includes:

- database host, port, and database name
- database username and password
- JWT secret
- JWT expiration duration
- backend server port
- mobile app API base URL

Example environment variables used by the backend:

```bash
DB_URL=jdbc:postgresql://localhost:5432/eventqr
DB_USERNAME=postgres
DB_PASSWORD=postgres
JWT_SECRET=your_jwt_secret_here
JWT_EXPIRATION_MS=86400000
PORT=10000
```

Do not commit real secrets, production URLs, or private deployment values to version control.

## Database migrations (Flyway) — fresh-deploy path

- The schema baseline lives in `V16__baseline_schema.sql` (`CREATE TABLE IF NOT EXISTS`, idempotent). On a fresh (empty) database, Flyway runs V16 then applies V17+ as normal migrations; Hibernate's `ddl-auto=validate` is safe because it only verifies the entity mapping against the migrated schema.
- Never change `spring.jpa.hibernate.ddl-auto` back to `update` or `create` on any environment that Flyway has already migrated — the two approaches fight over schema ownership and Flyway checksums drift.
- `spring.flyway.baseline-on-migrate=true` is only meant for pre-existing non-Flyway databases; a clean deploy does not rely on it.

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
