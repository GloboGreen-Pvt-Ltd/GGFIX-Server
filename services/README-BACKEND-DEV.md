# Run backend with in-memory DB (no PostgreSQL)

Use **dev** profile so both services use H2 in-memory and seed dummy data.

## Dummy data

- **Auth (8081):** User **barani** / **barani** (SUPER_ADMIN), plus an "Admin" shop.
- **Master Data (8091):** Brands (Apple, Samsung, Google, OnePlus, Xiaomi), models per brand, RAM options, storage options, repair services.

## Option 1: From repo root (PowerShell)

```powershell
.\run-backend-dev.ps1
```

Requires `mvn` on PATH. Two console windows will open (auth + master-data). Wait until each shows `Started ...Application`.

## Option 2: Maven in two terminals

**Terminal 1 – Auth**
```bash
cd services/auth-service
mvn -DskipTests spring-boot:run -Dspring-boot.run.profiles=dev
```

**Terminal 2 – Master Data**
```bash
cd services/master-data-service
mvn -DskipTests spring-boot:run -Dspring-boot.run.profiles=dev
```

## Option 3: From IDE

1. **Auth:** Run `AuthServiceApplication` (auth-service).  
   VM option: `-Dspring.profiles.active=dev`
2. **Master Data:** Run `MasterDataServiceApplication` (master-data-service).  
   VM option: `-Dspring.profiles.active=dev`

## Ports

| Service        | Port | Notes                    |
|----------------|------|---------------------------|
| auth-service   | 8081 | Login: barani / barani    |
| master-data-service | 8091 | Master data for admin & mobile |

## Admin panel

In `repair-shop-admin` set `.env.local`:

- `NEXT_PUBLIC_AUTH_BASE=http://localhost:8081`
- `NEXT_PUBLIC_API_BASE=http://localhost:8091`

Then open the admin app, log in with **barani** / **barani**, and use Master Data to run CRUD on brands, models, repair services, RAM, and storage.

## Later: PostgreSQL

When you switch to PostgreSQL, remove the dev profile and set DB env vars (or use default `localhost:5432/repairshop`). Seeders only insert when the DB is empty.

## Push notifications (FCM / Firebase Admin SDK)

`common-notify` sends pushes to the NearGFix Customer, Partner and Employee apps
over FCM (native Android tokens) or Expo (Expo push tokens). Apps register with
`POST /order/push-tokens` `{ token, platform, app }` (Partner also keeps
`POST /order/shop/push-tokens`). Table: `push_device_tokens` (migration 105).

**The service-account JSON is never committed, copied into the repo, a jar, an
image, a mobile app or `.env`** (`.gitignore` blocks `*-adminsdk-*.json`).

- **Local:** keep the key file outside the repo and point Application Default
  Credentials at it, e.g. PowerShell
  `[Environment]::SetEnvironmentVariable('GOOGLE_APPLICATION_CREDENTIALS','C:\path\to\neargfix-firebase-adminsdk-....json','User')`,
  then open a new terminal. Check with
  `mvn -f services/pom.xml -pl common-notify -am test -Dtest=FcmCredentialsCheckTest -Dsurefire.failIfNoSpecifiedTests=false`
  (a dry-run send — nothing reaches any device; skipped when no credentials are set).
- **Production (EC2):** the JSON lives in AWS Secrets Manager; services read it
  with the instance role. Set repo variables `FIREBASE_CREDENTIALS_SECRET_ID`
  (e.g. `neargfix/firebase-admin`) and optionally `FIREBASE_CREDENTIALS_SECRET_REGION`
  (default `AWS_REGION`). Do not set `GOOGLE_APPLICATION_CREDENTIALS` on the box.
- **Workload identity federation:** instead of a key, point
  `GOOGLE_APPLICATION_CREDENTIALS` at a WIF `external_account` config file (it
  contains no secret); Application Default Credentials picks it up.
- `FCM_ENABLED=false` turns FCM off; `PUSH_ENABLED=false` stops all sends.
  Missing/broken credentials never stop a service from booting — FCM sends are
  skipped and logged (no credential content is ever logged).
