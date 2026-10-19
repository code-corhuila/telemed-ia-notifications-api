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

## User story scope

- **HU-03** — deliver the password recovery e-mail from `PasswordRecoveryRequested` (in progress).
- **HU-17 / HU-18** — appointment notifications and reminders (not started).

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
