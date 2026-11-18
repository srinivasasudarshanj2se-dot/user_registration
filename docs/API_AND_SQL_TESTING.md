# User Registration API and SQL Testing Guide

## Live application and API links

| Deployment | Application | API base URL | Swagger UI | OpenAPI JSON | Health |
|---|---|---|---|---|---|
| Cloud Run | https://user-registration-gateway-z56jr6utaq-el.a.run.app | https://user-registration-api-986730329858.asia-south1.run.app | https://user-registration-api-986730329858.asia-south1.run.app/swagger-ui.html | https://user-registration-api-986730329858.asia-south1.run.app/v3/api-docs | https://user-registration-api-986730329858.asia-south1.run.app/actuator/health |
| Kubernetes | http://8.231.121.126 | http://8.231.121.126 | http://8.231.121.126/swagger-ui.html | http://8.231.121.126/v3/api-docs | http://8.231.121.126/actuator/health |

The Cloud Run gateway also proxies `/api/**` to the API. On Kubernetes, use the public gateway IP for API requests. Health, Swagger, and OpenAPI endpoints were checked on both deployments; Swagger may redirect from `/swagger-ui.html` to its UI page.

## Endpoints

Replace `{API_BASE}` with the matching API base URL from the table. JSON requests use `Content-Type: application/json`. Protected endpoints require `Authorization: Bearer <accessToken>` from the login response.

| Method | Path | Purpose | Authorization |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Register a new account | Public |
| POST | `/api/v1/auth/login` | Sign in and obtain access/refresh tokens | Public |
| POST | `/api/v1/auth/refresh-token` | Rotate tokens using a refresh token | Public |
| POST | `/api/v1/users` | Create a user | Admin |
| GET | `/api/v1/users?page=0&size=10` | List users; optional `email` filter | Admin |
| GET | `/api/v1/users/search?email=<text>&page=0&size=10` | Search users by email | Admin |
| GET | `/api/v1/users/{id}` | Read a user by ID | Admin |
| PUT | `/api/v1/users/{id}` | Update a user, including email | Admin |
| DELETE | `/api/v1/users/{id}` | Permanently delete a user | Admin |
| GET | `/api/v1/users/me` | Read the signed-in user profile | User or admin |
| PUT | `/api/v1/users/me` | Update the signed-in user profile | User or admin |
| POST | `/api/v1/calls` | Queue a call event for asynchronous processing | Admin |
| GET | `/api/v1/calls?limit=50` | List the latest processed call events (limit 1–100) | Admin |

Registration and the admin user-create endpoint require `firstName`, `lastName`, `email`, `mobile`, and `password`. Password length is 8–72 characters; mobile must contain 7–20 allowed digits/space/parenthesis/plus/hyphen characters. Registration creates a normal user account. The self-profile update accepts only `firstName`, `lastName`, and `mobile`; users cannot change their own email or role through that route.

## Test with curl

Examples below use the Cloud Run API. Set `API_BASE` to `http://8.231.121.126` to run the same API requests against Kubernetes. Use a new email for each registration; do not put real passwords in shell history or shared files.

```sh
API_BASE="https://user-registration-api-986730329858.asia-south1.run.app"

# Register
curl -i "$API_BASE/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Test","lastName":"User","email":"unique-test@example.com","mobile":"+1 555 010 2000","password":"ReplaceWithAStrongPassword1!"}'

# Log in, then copy accessToken from the JSON response
curl -i "$API_BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"unique-test@example.com","password":"ReplaceWithAStrongPassword1!"}'

TOKEN="<accessToken from login response>"
USER_ID=123 # Replace with the ID of a test user

# Read and update your own profile
curl -i "$API_BASE/api/v1/users/me" -H "Authorization: Bearer $TOKEN"
curl -i -X PUT "$API_BASE/api/v1/users/me" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"firstName":"Test","lastName":"User Updated","mobile":"+1 555 010 2001"}'

# Admin-only examples: use an admin access token
curl -i "$API_BASE/api/v1/users?page=0&size=10" -H "Authorization: Bearer $TOKEN"
curl -i "$API_BASE/api/v1/users/search?email=test&page=0&size=10" -H "Authorization: Bearer $TOKEN"
curl -i -X POST "$API_BASE/api/v1/users" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"firstName":"Directory","lastName":"User","email":"directory-test@example.com","mobile":"+1 555 010 2002","password":"ReplaceWithAStrongPassword1!"}'
curl -i "$API_BASE/api/v1/users/$USER_ID" -H "Authorization: Bearer $TOKEN"
curl -i -X PUT "$API_BASE/api/v1/users/$USER_ID" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"firstName":"Directory","lastName":"Updated","email":"directory-test@example.com","mobile":"+1 555 010 2003"}'
# Destructive; only run against a test user you intend to delete:
curl -i -X DELETE "$API_BASE/api/v1/users/$USER_ID" -H "Authorization: Bearer $TOKEN"
```

Expected success statuses include `201 Created` for registration/user creation, `200 OK` for reads/updates/login, and `204 No Content` for deletion. Duplicate emails conflict. Admin-only endpoints return forbidden for a normal user; protected endpoints require a valid access token. The frontend is available at the application link in the table.

## SQL and database schema

The database schema is managed by Flyway. Its source migration is [`V1__create_user_auth_schema.sql`](../src/main/resources/db/migration/V1__create_user_auth_schema.sql); application startup applies it automatically. Hibernate validates the schema and does not create or alter tables. Do not manually rerun the migration on a live database.

The call pipeline adds [`V2__create_call_events.sql`](../src/main/resources/db/migration/V2__create_call_events.sql). Admins can submit events with `callId`, `callerNumber`, `direction` (`INBOUND`/`OUTBOUND`), `status` (`RINGING`/`IN_PROGRESS`/`COMPLETED`/`MISSED`), optional `agentName`, ISO-8601 `startedAt`, and optional ISO-8601 `endedAt`. The API returns `202 Accepted` when Pub/Sub accepts the event. The Cloud Function consumes the topic and writes idempotently to Cloud SQL and BigQuery; the dashboard reads processed events from Cloud SQL and refreshes every five seconds.

The live-call dashboard and `/api/v1/calls` API are included in the Terraform Cloud Run stack. The existing independently managed Cloud Run/Kubernetes deployments are not automatically provisioned with the new topic, function, or permissions. Do not expect the call feature to work on those existing URLs until the matching infrastructure and application image are deployed. Caller numbers are sensitive personal data; restrict administrator access, retention, and BigQuery access to your approved operational policy.

Example request (after deploying the Terraform call pipeline, set `CALL_API_BASE` to its
`api_url` output and replace the bearer token with an administrator access token):

```sh
CALL_API_BASE="<api_url output from Terraform>"
curl -i -X POST "$CALL_API_BASE/api/v1/calls" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"callId":"test-call-001","callerNumber":"+1 555 010 2000","direction":"INBOUND","status":"IN_PROGRESS","agentName":"Test Agent","startedAt":"2026-01-02T03:04:05Z","endedAt":null}'

curl -i "$CALL_API_BASE/api/v1/calls?limit=50" \
  -H "Authorization: Bearer $TOKEN"
```

The migration creates:

| Table | Contents and constraints |
|---|---|
| `users` | User identity/profile, BCrypt-encoded password, enabled flag, timestamps; unique `uuid` and `email` |
| `roles` | Unique role names; migration seeds `ROLE_USER` and `ROLE_ADMIN` |
| `user_roles` | User-to-role join table; composite primary key and cascading foreign keys |
| `refresh_tokens` | Refresh-token hashes, owning user, expiry; unique token hash and cascading user foreign key |
| `call_events` | Processed customer call event details; call ID primary key and start-time index |

For read-only inspection, connect to the configured MySQL database with an authorized database client and run:

```sql
SELECT id, uuid, first_name, last_name, email, mobile, enabled, created_at, updated_at
FROM users
ORDER BY id;

SELECT u.id, u.email, r.name AS role
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
ORDER BY u.id, r.name;

SELECT id, user_id, expiry_date
FROM refresh_tokens
ORDER BY id;

SELECT call_id, caller_number, direction, status, agent_name, started_at, ended_at
FROM call_events
ORDER BY started_at DESC
LIMIT 50;
```

Never query, export, or share the `users.password` column; it contains password hashes. Do not run ad hoc `INSERT`, `UPDATE`, or `DELETE` statements against production data. Use the API for user changes so authorization, validation, password hashing, and audit logging remain in effect.

## Postman

Import [`User_Registration_API.postman_collection.json`](../postman/User_Registration_API.postman_collection.json). Its `apiBaseUrl` defaults to the Cloud Run API. Set it to `http://8.231.121.126` for Kubernetes. Run Register with the collection's generated unique email, then Login; the collection saves the returned tokens for subsequent requests. CRUD operations that require an administrator need a valid admin token.
