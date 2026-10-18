# User Registration Platform — High-Level Design

## 1. Purpose

This document describes the design for the user-registration platform: an Angular web client, Spring Boot services for authentication and user management, a Spring Cloud API gateway, and shared platform capabilities for persistence, telemetry, and deployment.

It also records the implementation boundary of this repository. The current codebase contains a single Spring Boot application containing both authentication and user CRUD, an Angular 20 UI, Docker Compose files for a local stack, and baseline Kubernetes manifests. It does not yet contain the API gateway, independently deployable auth/user services, or full observability stack. The target architecture below is a design direction, not a claim that those components are already implemented.

## 2. Goals and scope

### Goals

- Provide user registration, login, JWT-based access, refresh-token rotation, profile management, search, and pagination.
- Keep application APIs stateless and versioned.
- Validate requests and return consistent client-facing errors.
- Support health checks, metrics, structured logs, and distributed tracing as the platform grows.
- Allow the current backend to run locally without MySQL using its demo profile.

### Out of scope for the current implementation

- OAuth 2.0 Authorization Server features such as authorization-code grants, consent, and OIDC.
- API gateway routing and independently deployed microservices.
- Prometheus/Grafana/Jaeger and a configured OpenTelemetry Collector.
- Fine-grained administrative role-management APIs.

## 3. Architecture

### Target logical architecture

```text
                          +------------------------+
                          | Angular 20+ Web Client |
                          | Login / Register / UI  |
                          +-----------+------------+
                                      |
                           HTTPS, JSON, Bearer JWT
                                      |
                          +-----------v------------+
                          | Spring Cloud API       |
                          | Gateway                |
                          | routing / CORS / limits|
                          +------+------------+----+
                                 |            |
                 /api/v1/auth/** |            | /api/v1/users/**
                                 |            |
                  +--------------v--+      +--v-----------------+
                  | Auth Service    |      | User Service       |
                  | register/login  |      | user/profile CRUD  |
                  | issue/rotate JWT|      | search/pagination  |
                  +--------+--------+      +---------+----------+
                           |                         |
                           +------------+------------+
                                        |
                              +---------v----------+
                              | MySQL              |
                              | users, roles,      |
                              | user_roles,        |
                              | refresh_tokens     |
                              +--------------------+

  Services -- OTLP --> OpenTelemetry Collector --> Prometheus / Jaeger
                                                    |
                                                    v
                                                 Grafana
```

### Current repository architecture

```text
Client / API caller --> Spring Boot application :8081 --> MySQL
                                                  \----> Actuator health / Prometheus

Optional local demo: Spring Boot application :8081 --> in-memory H2
```

Authentication and user CRUD currently share one deployment, one application configuration, and one database. This is intentionally the smallest runnable version of the backend flows; extracting services and adding a gateway can be done later without changing the public API paths.

## 4. Components and responsibilities

| Component | Target responsibilities | Target port | Repository status |
|---|---|---:|---|
| Angular UI | Login/register forms, dashboard, user grid, search, pagination, client-side navigation and HTTP integration | 4200 (development) | Present in `angular-ui`; Angular Material; auth guard/interceptor |
| API gateway | Route requests to services; centralize edge CORS, rate limiting, and gateway observability | 8080 (proposed) | Not present |
| Auth service | Registration, password verification, access-token issuance, refresh-token rotation, role assignment | 8081 | Present inside the single application |
| User service | Authenticated user CRUD, profile updates, search, and pagination | 8082 | Present inside the single application |
| MySQL | Durable users, roles, user-role relationships, and refresh-token hashes | 3306 (typical) | Configured; Flyway migration is present |
| Telemetry stack | Collect and expose metrics, traces, and service logs | Varies | Not configured |

The target gateway port and the UI development port are proposed conventions. Only the backend application port `8081` is configured in this repository today.

## 5. API design

All application endpoints are versioned under `/api/v1`. JSON is used for request and response bodies. Protected endpoints require `Authorization: Bearer <access-token>`.

| Method | Path | Access | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | Validate input, create account, assign `ROLE_USER` |
| `POST` | `/api/v1/auth/login` | Public | Verify credentials and issue access and refresh tokens |
| `POST` | `/api/v1/auth/refresh-token` | Public, refresh credential required | Revoke the supplied refresh token and issue a replacement pair |
| `POST` | `/api/v1/users` | Authenticated | Create a user |
| `GET` | `/api/v1/users?page=0&size=10` | Authenticated | Return a page of users |
| `GET` | `/api/v1/users/search?email=...` | Authenticated | Search users by email |
| `GET` | `/api/v1/users/{id}` | Authenticated | Get a user by numeric ID |
| `PUT` | `/api/v1/users/{id}` | Authenticated | Update profile fields |
| `DELETE` | `/api/v1/users/{id}` | Authenticated | Delete a user |
| `GET` | `/actuator/health` | Public | Runtime health probe |
| `GET` | `/actuator/prometheus` | Public | Prometheus scrape endpoint |

The current application exposes these API paths directly on port `8081`, and the Angular development server serves the client on port `4200`. In the target deployment, the gateway exposes them externally and routes the auth and user prefixes to their respective services.

### Error shape

Application errors use a JSON object with a stable error `code` and a human-readable `message`. Expected HTTP outcomes include `400` for invalid input, `401` for invalid credentials or a missing/invalid token, `404` for a missing user, `409` for duplicate email, and `204` for a successful delete.

## 6. Main request flows

### Registration

1. The client submits first name, last name, email, mobile number, and password to `/api/v1/auth/register`.
2. The auth service validates the request and normalizes the email for case-insensitive uniqueness.
3. It checks for an existing account, hashes the password with BCrypt, assigns the default `ROLE_USER`, and persists the account.
4. It returns a success response. Registration does not return a password or password hash.

### Login and access-token use

1. The client submits email and password to `/api/v1/auth/login`.
2. The auth service looks up the account and compares the password against its BCrypt hash. Invalid credentials return the same generic authentication error.
3. It signs a short-lived HS256 JWT with the configured issuer, subject, issue/expiry times, token type `access`, and role claims.
4. It generates a cryptographically random opaque refresh token and persists only its SHA-256 hash and expiry.
5. The client includes the access token as a bearer token on protected API requests. The backend validates its signature, issuer, expiry, and token type.

### Refresh-token rotation

1. The client sends the opaque refresh token to `/api/v1/auth/refresh-token`.
2. The auth service hashes the presented value and finds the corresponding record under a database write lock.
3. If it is unknown, expired, or associated with a disabled account, the request fails with `401`.
4. Otherwise, the old record is removed and a new access/refresh-token pair is issued in the same transaction. Reuse of the old token is rejected.

### User operations

The client calls `/api/v1/users` with a valid bearer access token. The user service validates input, applies the requested CRUD/search operation, and returns a DTO that excludes the password hash. Listing and email search use Spring Data pagination. The current design requires authentication for user operations; it does not yet restrict records to the caller's own account or provide a complete administrator authorization policy.

## 7. Data model

Flyway migration `V1__create_user_auth_schema.sql` defines the current schema:

```text
users
  id            BIGINT primary key, generated
  uuid          VARCHAR(50) unique, non-null
  first_name    VARCHAR(100) non-null
  last_name     VARCHAR(100) non-null
  email         VARCHAR(255) unique, non-null
  mobile        VARCHAR(20) non-null
  password      VARCHAR(255) non-null; BCrypt hash only
  enabled       BOOLEAN non-null
  created_at    TIMESTAMP non-null
  updated_at    TIMESTAMP non-null

roles
  id            BIGINT primary key, generated
  name          VARCHAR(50) unique, non-null

user_roles
  user_id       BIGINT foreign key -> users.id
  role_id       BIGINT foreign key -> roles.id
  primary key (user_id, role_id)

refresh_tokens
  id            BIGINT primary key, generated
  token_hash    VARCHAR(64) unique, non-null
  user_id       BIGINT foreign key -> users.id
  expiry_date   TIMESTAMP non-null
```

The migration seeds `ROLE_USER` and `ROLE_ADMIN`. Database foreign keys cascade the removal of role links and refresh-token records when a user is deleted. Hibernate runs in schema-validation mode; Flyway is the source of truth for schema changes.

In the target microservice deployment, the initial design keeps a shared MySQL schema to match the supplied project requirements. If independent service ownership or independent scaling becomes a priority, move toward a database/schema per service and exchange data through APIs or events rather than direct cross-service table access.

## 8. Security design

- Require HTTPS outside local development.
- Store only adaptive password hashes (BCrypt), never plaintext passwords.
- Use signed, short-lived access JWTs and validate signature, issuer, expiry, and `token_type=access`.
- Generate refresh tokens with a cryptographically secure random generator; store only SHA-256 hashes; rotate once per use.
- Keep signing secrets and database credentials in deployment secrets or environment configuration. The committed development JWT secret is not suitable for production.
- Validate request fields and bound field lengths; normalize email consistently.
- Do not serialize password hashes or refresh credentials into user responses or logs.
- Permit only the configured web-client origin through CORS. Configure allowed origins explicitly in deployed environments.
- Keep actuator exposure limited to health, info, and Prometheus endpoints; put metrics behind network policy or gateway authorization in production.
- Use least-privilege service/database accounts. Avoid exposing the database publicly.
- Add authorization policy for roles and user ownership before exposing sensitive multi-user operations to end users.

The JWT signing key is symmetric in the current implementation. For independently deployed services, provision the same protected verification key to each resource server or migrate to asymmetric signing (for example, an authorization server signing with a private key while APIs validate with a public key).

## 9. Configuration and local execution

The default Spring profile connects to MySQL using:

- `DB_URL` (default `jdbc:mysql://localhost:3306/user_registration`)
- `DB_USERNAME` (default `root`)
- `DB_PASSWORD` (default `password`)
- `SERVER_PORT` (default `8081`)
- `JWT_SECRET` (base64-encoded key; must decode to at least 32 bytes)
- `JWT_ISSUER`, `JWT_ACCESS_TOKEN_TTL`, and `JWT_REFRESH_TOKEN_TTL`

For a disposable local demo that does not require MySQL:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=demo"
```

The `demo` profile uses in-memory H2 and is reset when the application stops. For the test profile, the integration test uses a separate in-memory H2 database. Flyway applies the same migration in both environments.

Run the Angular client in a second terminal:

```powershell
npm --prefix angular-ui install
npm --prefix angular-ui start -- --host 127.0.0.1
```

Open `http://localhost:4200`. The Angular dev-server proxy forwards API calls to `http://localhost:8081`.

Docker Compose and Kubernetes setup, environment variables, secret creation, image builds, deployment, and local access are documented in [`kubernetes/README.md`](kubernetes/README.md). The root Compose file runs MySQL, the API, and the UI; Kubernetes uses a MySQL StatefulSet and API/UI Deployments.

## 10. Observability and operations

The current backend exposes Actuator health and Prometheus endpoints and configures a sampling probability for tracing. A production platform should additionally:

- Export traces over OTLP to an OpenTelemetry Collector and from there to a trace backend such as Jaeger.
- Scrape service metrics with Prometheus and build dashboards/alerts in Grafana.
- Emit structured JSON logs to a centralized log platform, including request correlation/trace identifiers but excluding credentials, tokens, and sensitive personal data.
- Monitor request rate, error rate, latency, JVM health, database connection-pool utilization, and refresh/login failures.
- Separate liveness and readiness probes; readiness should reflect whether the service can reach its required database.
- Set explicit resource requests/limits and graceful shutdown behavior in container orchestration.

The tracing exporter, JSON logging configuration, Collector, Prometheus scraping configuration, Grafana dashboards, and alert rules have not yet been added to this repository.

## 11. Deployment evolution

1. **Current runnable stack:** one Spring Boot application on port `8081`, an Angular UI, MySQL and Flyway, a Docker Compose stack, baseline Kubernetes manifests, and H2 profiles for tests and local demo.
2. **Service extraction:** split authentication and user-management packages into separate deployable services while preserving `/api/v1` contracts and coordinating schema ownership.
3. **Gateway:** add a Spring Cloud Gateway in front of the existing Angular client and backend APIs; configure proxy routing, edge CORS, and rate limiting.
4. **Operational platform:** configure OTLP exporters, Collector, Prometheus scraping, Grafana dashboards, alerts, and production storage/backup policies.
5. **Production hardening:** establish role and ownership policy, key rotation, rate limits, backups, data retention, migration rollout procedures, and security monitoring.

## 12. Important design decisions and limitations

- The repository remains a single application until service extraction is implemented; the target service boundaries do not imply that a gateway or two running services already exist.
- Docker Compose and Kubernetes starter files are present, but the API gateway, separately deployed auth/user services, and full telemetry stack are not implemented.
- Access tokens last one hour by default; refresh tokens last seven days by default. Tune these values against the deployment's threat model.
- Refresh-token revocation is stateful and depends on the database. Access tokens remain valid until expiry unless a future revocation/versioning mechanism is introduced.
- Authentication protects user endpoints, but the current implementation does not yet enforce per-user ownership or role-based access decisions for each operation.
