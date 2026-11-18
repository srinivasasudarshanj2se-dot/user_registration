# User Registration Platform — High-Level Design

## 1. Purpose

This document describes the implemented user-registration platform and provides a step-by-step guide to its request, data, and deployment flows.

The current codebase contains an Angular 20 UI, a separate Spring WebFlux API gateway that uses WebClient for asynchronous HTTP forwarding, one Spring Boot application containing both authentication and user-management modules, Flyway-managed MySQL persistence, and deployment configurations for local Docker Compose, Cloud Run, and GKE. The gateway provides a single entry point and routes API paths to the backend and other paths to the UI. Cloud Run and GKE use Cloud SQL for MySQL. Auth and user management are not independently deployable services, and a full observability stack is not configured.

## 2. Goals and scope

### Goals

- Provide user registration, login, JWT-based access, refresh-token rotation, profile management, search, and pagination.
- Keep application APIs stateless and versioned.
- Validate requests and return consistent client-facing errors.
- Support health checks, metrics, structured logs, and distributed tracing as the platform grows.
- Allow the current backend to run locally without MySQL using its demo profile.

### Out of scope for the current implementation

- OAuth 2.0 Authorization Server features such as authorization-code grants, consent, and OIDC.
- Independently deployed auth and user microservices.
- Prometheus/Grafana/Jaeger and a configured OpenTelemetry Collector.
- Fine-grained administrative role-management APIs.

## 3. Architecture

### Current deployed topology

```text
Cloud Run:
Browser --> Cloud Run API Gateway -- /api, /actuator --> Cloud Run API
             |                                           |
             +-- other paths --> Cloud Run UI             +--> Cloud SQL

GKE:
Browser --> public LoadBalancer --> Gateway -- /api, /actuator --> internal API Service
                                      |                               |
                                      +-- other paths --> UI          +--> Cloud SQL

Both API deployments share the same MySQL database.
```

The Cloud Run gateway routes to the public API and UI services; the GKE gateway routes to internal Kubernetes services. Only the gateway is exposed through the GKE public LoadBalancer. The gateway is a reverse proxy, not an OAuth authorization server: the API validates bearer JWTs and enforces roles. Cloud SQL is the shared persistent store.

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
| `GET` | `/v3/api-docs` | Public | OpenAPI 3 specification |
| `GET` | `/swagger-ui.html` | Public | Swagger UI documentation |

The current application exposes these API paths directly on port `8081`, and the Angular development server serves the client on port `4200`. Swagger UI documents the current controller endpoints and supports the Bearer JWT scheme for user operations. In the target deployment, the gateway exposes them externally and routes the auth and user prefixes to their respective services.

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

### Complete application flow: step by step

1. **Choose a runtime configuration.** Local Docker Compose supplies a MySQL container and environment variables from `.env`. The demo Spring profile uses in-memory H2 and loses its data when the process stops. Cloud deployments supply the database URL and credentials for Cloud SQL.
2. **Start the API and initialize storage.** Spring Boot reads `application.yaml` plus environment overrides. Flyway applies pending migrations; Hibernate validates that the entity mappings match the migrated schema. The API exposes its configured port and Actuator health endpoint.
3. **Load the UI.** The browser downloads the Angular single-page application. Nginx serves static assets and routes browser API requests under `/api` to the backend. The Cloud Run UI proxies to the Cloud Run API URL; GKE UI proxies to the internal Kubernetes `api` Service.
4. **Register an account.** Angular validates the form, then sends JSON to `POST /api/v1/auth/register`. `AuthController` applies request DTO validation and calls `AuthService`. The service normalizes email, checks uniqueness, loads `ROLE_USER`, BCrypt-hashes the password, and persists the user and role association. It returns success without returning a password or hash.
5. **Sign in.** The browser sends credentials to `POST /api/v1/auth/login`. The service finds the user case-insensitively, verifies the enabled flag and BCrypt password, then returns an HS256 access JWT and a random refresh token. The database stores only the refresh-token SHA-256 hash and expiration.
6. **Call a protected endpoint.** Angular stores the tokens in `sessionStorage`; its interceptor attaches the access token as a Bearer credential to non-auth API calls. Spring Security validates the JWT signature, issuer, expiry, and access-token type before routing the request to the user controller.
7. **Read or change directory data.** `UserController` delegates to `UserService`, which validates input and uses Spring Data repositories. The user response DTO excludes the password hash. MySQL constraints protect uniqueness and references. Listing/search use pagination.
8. **Refresh or end a client session.** The refresh endpoint hashes the supplied token, looks it up under a database write lock, checks expiry and account status, deletes the old record, and issues a replacement pair. The Angular UI does not currently automate this call; on a protected `401`, it clears browser tokens and routes to login. Client logout clears local tokens but does not revoke already issued access JWTs.
9. **Handle failures and observe the service.** `ApiExceptionHandler` maps expected validation, authentication, not-found, and conflict exceptions to JSON error responses. SLF4J logs application events to process output. Actuator health and Prometheus endpoints are exposed; a complete central logging/tracing/alerting stack is not configured.

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
- `DB_USERNAME` (default `registration_app`)
- `DB_PASSWORD` (default `admin`; local development only)
- `SERVER_PORT` (default `8081`)
- `JWT_SECRET` (base64-encoded key; must decode to at least 32 bytes)
- `JWT_ISSUER`, `JWT_ACCESS_TOKEN_TTL`, and `JWT_REFRESH_TOKEN_TTL`

The database username and password defaults are convenient for local development only. Use a least-privilege account and inject strong credentials through environment variables or a secret manager outside local development. The Docker Compose stack reads its required database and JWT values from `.env`; it does not use the application defaults for those required secrets.

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

### Cloud deployment flow: step by step

The deployed Cloud Run and GKE APIs use the same Cloud SQL instance and database. Current demo configuration documented by this repository:

| Property | Current demo value |
|---|---|
| Cloud SQL instance | `user-registration-mysql` |
| Region | `asia-south1` |
| Engine / database | MySQL 8.0 / `user_registration` |
| Instance sizing | `db-f1-micro`, 10 GiB SSD, zonal |
| Backups | Automated daily backups enabled |
| Connection | Cloud SQL Java Connector, using the instance connection name |

Deployment sequence:

1. **Build and publish images.** Build API and UI container images, tag them for the deployment, and push them to the `user-registration` Artifact Registry repository in the deployment region.
2. **Prepare Cloud SQL.** Create the MySQL instance and `user_registration` database. The application connects through the Cloud SQL Java Connector; it does not depend on a public database IP allowlist.
3. **Prepare identity and secrets.** Grant the Cloud Run runtime service account `roles/cloudsql.client` and access to the necessary Secret Manager secrets. For GKE, grant Cloud SQL access to the Google service account linked to the API Kubernetes service account through Workload Identity. Supply database username/password and JWT signing secret at runtime; never put real secret values in the repository or container image.
4. **Deploy the Cloud Run API.** Configure the API image, Cloud SQL instance attachment, runtime service account, database URL, database credentials, JWT issuer/secret, and UI CORS origin. Cloud Run supplies the `PORT` environment variable.
5. **Deploy the Cloud Run UI and gateway.** Configure the UI's Nginx `API_UPSTREAM` with the Cloud Run API URL, then deploy the Spring WebFlux gateway with both Cloud Run service URLs as upstreams. Its WebClient forwards browser/API requests; use the gateway URL for application traffic.
6. **Deploy to GKE.** Create/target the GKE cluster and namespace, apply the Cloud SQL ConfigMap and Workload Identity service account, create the Kubernetes Secret, then apply the API, UI, and gateway Deployments. Set their images to the published Artifact Registry references. Skip the local MySQL StatefulSet for the Cloud SQL deployment.
7. **Expose and verify.** Expose the GKE gateway with `kubernetes/public-gateway-service.yaml`; keep API and UI as internal ClusterIP services. Wait for Deployment rollouts, inspect health/logs, then test registration, login, and API routes through the gateway.

Relevant configuration and implementation files:

- `src/main/resources/application.yaml` — datasource environment-variable mapping, Flyway, Hibernate validation, and server port.
- `build.gradle` — MySQL driver and Cloud SQL Java Connector dependencies.
- `src/main/resources/db/migration/V1__create_user_auth_schema.sql` — database schema and initial roles.
- `kubernetes/gke-cloudsql-configmap.yaml` — GKE JDBC URL / instance connection name and non-secret settings.
- `kubernetes/gke-cloudsql-service-account.yaml` — Kubernetes-to-Google service-account association for Workload Identity.
- `gateway/src/main/java/org/example/user_registration/gateway/GatewayController.java` — WebClient-based streaming reverse proxy.
- `gateway/src/main/java/org/example/user_registration/gateway/WebClientConfiguration.java` — WebClient connector and timeout configuration.
- `gateway/build.gradle` and `gateway/Dockerfile` — gateway module dependencies and container image.
- `kubernetes/api.yaml` — API environment references, probes, resources, and internal Service.
- `kubernetes/ui.yaml` and `kubernetes/public-gateway-service.yaml` — UI Deployment and public gateway LoadBalancer.
- `kubernetes/README.md` — step-by-step provisioning and deployment commands.

Previously verified service endpoints (availability depends on the GCP resources remaining active; the gateway itself has not yet been deployed):

- Cloud Run API Gateway: deploy `user-registration-gateway` following [`kubernetes/README.md`](kubernetes/README.md); the gateway service URL is the single app/API entry point.
- Cloud Run UI: <https://user-registration-ui-z56jr6utaq-el.a.run.app>
- Cloud Run API: <https://user-registration-api-z56jr6utaq-el.a.run.app>
- Cloud Run Swagger UI: <https://user-registration-api-z56jr6utaq-el.a.run.app/swagger-ui.html>
- GKE gateway: expose the gateway with `kubernetes/public-gateway-service.yaml`; the assigned LoadBalancer IP is the single app/API entry point.
- Importable Postman requests: [`postman/User_Registration_API.postman_collection.json`](postman/User_Registration_API.postman_collection.json)

## 10. Observability and operations

The current backend exposes Actuator health and Prometheus endpoints and configures a sampling probability for tracing. SLF4J application logs record successful registration, authentication, refresh-token renewal, and user create/update/delete operations, along with handled authentication, validation, resource-not-found, and conflict errors. These logs avoid passwords, JWTs, refresh tokens, and email addresses. The application currently uses the configured logging backend's default output format; centralized structured logging and trace correlation are not configured.

A production platform should additionally:

- Export traces over OTLP to an OpenTelemetry Collector and from there to a trace backend such as Jaeger.
- Scrape service metrics with Prometheus and build dashboards/alerts in Grafana.
- Emit structured JSON logs to a centralized log platform, including request correlation/trace identifiers but excluding credentials, tokens, and sensitive personal data.
- Monitor request rate, error rate, latency, JVM health, database connection-pool utilization, and refresh/login failures.
- Separate liveness and readiness probes; readiness should reflect whether the service can reach its required database.
- Set explicit resource requests/limits and graceful shutdown behavior in container orchestration.

The tracing exporter, JSON logging configuration, Collector, Prometheus scraping configuration, Grafana dashboards, and alert rules have not yet been added to this repository.

## 11. Deployment evolution

1. **Current runnable stack:** one Spring Boot application on port `8081`, an Angular UI, MySQL and Flyway, a Spring WebFlux gateway using WebClient, a Docker Compose stack, baseline Kubernetes manifests, and H2 profiles for tests and local demo.
2. **Service extraction:** split authentication and user-management packages into separate deployable services while preserving `/api/v1` contracts and coordinating schema ownership.
3. **Gateway evolution:** the current Spring WebFlux gateway uses WebClient to route browser/API traffic. Spring Cloud Gateway can add service discovery, policy-based edge filters, and centralized rate limiting as backend services are split.
4. **Operational platform:** configure OTLP exporters, Collector, Prometheus scraping, Grafana dashboards, alerts, and production storage/backup policies.
5. **Production hardening:** establish role and ownership policy, key rotation, rate limits, backups, data retention, migration rollout procedures, and security monitoring.

## 12. Important design decisions and limitations

- The repository remains a single application with API, UI, and gateway runtime containers; the target service boundaries do not imply separate auth or user microservices.
- The Spring WebFlux gateway routes requests with WebClient but does not replace backend JWT validation or role authorization. Auth/user microservices and the full telemetry stack are not implemented.
- Access tokens last one hour by default; refresh tokens last seven days by default. Tune these values against the deployment's threat model.
- Refresh-token revocation is stateful and depends on the database. Access tokens remain valid until expiry unless a future revocation/versioning mechanism is introduced.
- The API validates issuer-bound JWT access tokens and maps the signed `roles` claim to Spring Security authorities. `ROLE_USER` accounts can read/update their own profile; `ROLE_ADMIN` is required for directory and user-management operations. Public registration never accepts roles; administrator role assignment is performed by a trusted operator.
