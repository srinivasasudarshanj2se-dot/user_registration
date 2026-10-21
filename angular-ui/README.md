# userspace UI

Angular 20 and Angular Material frontend for the user-registration backend.

## Start

Start the two Spring Boot services in separate terminals from the repository root. Both use their own in-memory H2 database in demo mode:

```powershell
.\gradlew.bat :services:user-service:bootRun --args="--spring.profiles.active=demo"
```

```powershell
.\gradlew.bat :services:auth-service:bootRun --args="--spring.profiles.active=demo"
```

In a third terminal, start the UI:

```powershell
npm install
npm start -- --host 127.0.0.1
```

Open [http://localhost:4200/login](http://localhost:4200/login). Create an account, sign in, and use the people directory to search, paginate, add, edit, and remove users. The Angular development proxy routes authentication to `http://localhost:8081` and user management to `http://localhost:8082`. API documentation is available at [Auth Swagger UI](http://localhost:8081/swagger-ui/index.html) and [User Swagger UI](http://localhost:8082/swagger-ui/index.html). The auth service registers accounts and issues JWTs; the user service owns user profiles and credentials in its separate database.

## Build

```powershell
npm run build
```

## Docker Compose

From the repository root, create `.env` from `.env.example`, change the local passwords and JWT/API keys, then run `docker compose up --build`. Compose starts separate auth and user services with their own MySQL databases and the Angular UI.
