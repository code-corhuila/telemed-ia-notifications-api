# telemed-ia-notifications-api

> Notifications bounded context: e-mail delivery triggered by domain events.

Part of **TeleMed IA**, Grupo 2. Governance and documentation live in `telemed-ia-docs`
(`09-microservices/services/08-notification-service/`).

## Modules

| Module | Content |
|---|---|
| `notifications-core` | Domain and application layer. No framework dependencies. |
| `notifications-adapters` | HTTP (correlation id, error envelope), persistence and, later, e-mail and messaging adapters. |
| `notifications-app` | Spring Boot composition root, configuration and E2E tests. |

Hexagonal architecture, same layout as `telemed-ia-identity-and-access-api`.

## Technology baseline

Java 21, Spring Boot 3.5.16, Maven multimodule, PostgreSQL 16.

## Database

- Schema `notifications` in the shared instance of `telemed-ia-infra-postgres`, owned by
  `telemed-ia-notifications-db`.
- The API connects as `notifications_app` (its `search_path` is `notifications`), **never runs
  migrations** (`spring.liquibase.enabled=false`) and validates the mapped tables
  (`ddl-auto=validate`).

## Running locally

```bash
cp .env.example .env              # set DB_PASSWORD
mvn -B package -Dtest='!*E2ETest' -Dsurefire.failIfNoSpecifiedTests=false
java -jar notifications-app/target/notifications-app.jar
curl http://localhost:9082/actuator/health
```

With Docker, on the `platform` network created by `telemed-ia-infra-postgres`:

```bash
docker compose --env-file .env -f deploy/compose.yml up -d --build
```

| Port | Use |
|---|---|
| 8082 | API |
| 9082 | Management (`/actuator/health`), bound to `127.0.0.1` in Compose |

## Verification

The E2E suite starts PostgreSQL with Testcontainers, reproduces the infra bootstrap of the
notifications domain and applies the real migrations of `telemed-ia-notifications-db` as
`notifications_app`:

```bash
NOTIFICATIONS_DB_MIGRATIONS_DIR=/path/to/telemed-ia-notifications-db mvn -B verify
```

CI checks out `telemed-ia-notifications-db` at a pinned commit and runs the same command.

## Error handling and correlation

- `X-Correlation-Id` is reused when it matches `[A-Za-z0-9._-]{1,128}`, generated otherwise,
  returned in the response and logged as `traceId`.
- Errors use the common envelope `{error, message, details, traceId}`; driver messages and
  stack traces are never exposed.
- The `prod` profile writes JSON logs (ECS format).

## Authentication

- Every `/api/v1` request needs `Authorization: Bearer <access token>` issued by identity. The token
  is verified here (NORMA): RS256 only, identity's public key, issuer, expiry, `jti`, a user UUID in
  `sub` and a `role`. Service tokens are not accepted.
- Any failure answers `401` with `WWW-Authenticate: Bearer` and the common envelope (`UNAUTHORIZED`),
  without saying why. Actuator, on the management port, is not affected.
- `JWT_PUBLIC_KEY` (escaped PEM) or `JWT_PUBLIC_KEY_FILE`; Docker Compose mounts
  `JWT_PUBLIC_KEY_HOST_FILE`. The service does not start without the key.

## Inbox API (HU-17)

Always the inbox of the access token's user; password recovery is never listed.

- `GET /api/v1/notifications?unread=true&type=APPOINTMENT_REMINDER&page=1&limit=20` returns `{data, meta}`:
  each item has `id`, `type`, `subject` (`{type, id, at}` or `null`), `read`, `readAt`, `createdAt`.
- `GET /api/v1/notifications/unread-count` returns `{unreadCount}`.
- `PUT /api/v1/notifications/{id}/read` and `PUT /api/v1/notifications/read-all` answer `204`.
  Another user's notification answers `404`, like a missing one.

## Identity client (HU-17)

- The patient's e-mail is asked to identity, `GET /internal/notification-recipients/{id}` (active patients
  only; any other account answers nothing), only while an e-mail is sent: it is never stored.
- Notifications authenticates as its own service client (`IDENTITY_CLIENT_ID`, default `notifications`, and
  `IDENTITY_CLIENT_SECRET`) at `POST /internal/service-token`; identity's `SERVICE_CLIENTS` keeps the bcrypt
  hash. The token is reused until 30 seconds before it expires, and renewed once if identity rejects it.
- Every call is limited by `IDENTITY_TIMEOUT` (default 3 s). If identity cannot answer, the event is retried through
  the broker. The secret is required only where the event consumers run (`NOTIFICATIONS_LISTENER_ENABLED`).

## E-mail

- Plain-text e-mails in Spanish over SMTP (`MAIL_*` variables, see `.env.example`). In `dev` the
  default is a local catcher on `localhost:1025`; `prod` requires `MAIL_HOST` and `MAIL_FROM` and
  uses STARTTLS with authentication.
- The recipient address and the reset link are used only while sending: they are not stored and
  not logged. A failed delivery is recorded as `FAILED` and retried through broker redelivery.
- The mail health indicator is disabled so an SMTP outage does not mark the service as down.

## Messaging

- Consumes `PasswordRecoveryRequested` (ADR-011 envelope) from the RabbitMQ topic exchange
  `identity.events`, routing key `password-recovery-requested`, through the queue it owns,
  `notifications.password-recovery-requested`. A repeated event id sends no second e-mail.
- An SMTP failure is retried 3 times with backoff; then, like a message that can never be delivered
  (malformed, another type or version), it goes to `notifications.password-recovery-requested.dlq`.
  Dead-lettered messages expire after 30 minutes, the lifetime of the reset link they carry.
- Consumes `ConsultationPdfGenerated` from `documents.events` (routing key
  `consultation-pdf-generated`, queue `notifications.consultation-pdf-generated` and its `.dlq`):
  the patient gets `DOCUMENT_READY` in the inbox only (channel `IN_APP`), once per event.
- Consumes `AppointmentCreated`, `AppointmentRescheduled` and `AppointmentCancelled` from
  `appointments.events` (routing keys `appointment-created`, `appointment-rescheduled`,
  `appointment-cancelled`; one queue, `notifications.appointment-events`, and its `.dlq`), as
  `APPOINTMENT_CONFIRMED`, `APPOINTMENT_RESCHEDULED` and `APPOINTMENT_CANCELLED`:
  - the professional sees it in the inbox only (channel `IN_APP`);
  - the patient gets one e-mail (Spanish, Bogota time, no names) at the address identity answers,
    and the same record shows it in the patient's inbox. A patient without an address keeps the
    notice in the inbox; identity or SMTP failures are retried through the broker.
- Consumes `AppointmentReminderDue` from the worker on the same exchange and queue (routing key
  `appointment-reminder-due`) as `APPOINTMENT_REMINDER`, delivered like the changes above. A
  reminder is dropped when the patient was already notified of that appointment's cancellation.
- The listener runs only with `NOTIFICATIONS_LISTENER_ENABLED=true` (`RABBITMQ_*` variables, see
  `.env.example`); Docker Compose turns it on. The RabbitMQ health indicator is disabled.

## User story scope

- **HU-03** — deliver the password recovery e-mail from `PasswordRecoveryRequested` (in progress).
- **HU-17 / HU-18** — appointment notifications, reminders and the notifications inbox (in progress).

## Branching

Three permanent branches. **None of them accepts a direct commit** — you enter through a child
branch and leave through a Pull Request.

```
develop  <--PR--  feat/... fix/... chore/...
qa       <--PR--  qa/...
main     <--PR--  release/...  hotfix/...
```

Promotion happens **by re-application** (`git cherry-pick -x`), never by merging one permanent
branch into another: `merge develop -> qa` and `merge qa -> main` do not exist in this model.

`main` requires **1 approval from `ariel5253`**. On `develop` and `qa` the team sets its own review
rule.

Full policy: `00-governance/branching-policy.md` in `library-docs`.
