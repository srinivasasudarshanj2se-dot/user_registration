# userspace UI

Angular 20 and Angular Material frontend for the user-registration backend.

## Start

Start the Spring Boot backend in demo mode from the repository root:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=demo"
```

Then start the UI in a second terminal:

```powershell
npm install
npm start -- --host 127.0.0.1
```

Open [http://localhost:4200/login](http://localhost:4200/login). Create an account, sign in, and use the people directory to search, paginate, add, edit, and remove users. The development API URL is `http://localhost:8081`, configured in `src/environments/environment.ts`.

## Build

```powershell
npm run build
```
