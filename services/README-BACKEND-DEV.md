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
