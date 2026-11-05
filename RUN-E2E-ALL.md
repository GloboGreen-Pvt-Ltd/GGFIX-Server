# Run everything for end-to-end testing (backend + admin + mobile)

Use this to run all backend services, the admin panel, and the mobile app so you can test the full flow.

---

## Prerequisites

- **Java 25+** and **Maven** (`mvn` on PATH) for the backend
- **Node.js** (LTS) for admin and mobile
- **Expo Go** on your phone (optional; for running the app on device)

---

## 1. Start the backend

### Option A: All 12 services (inventory, marketplace, order, notification, pickup, technician, etc.)

From **repair-shop-saas** folder:

```powershell
cd C:\Users\baran\repair-shop-saas
.\run-all-backend-dev.ps1
```

This opens **12 PowerShell windows** (auth, master-data, ticket, user, shop, technician, inventory, marketplace, pickup, notification, subscription, order). Each uses H2 in-memory with profile `dev`. Wait until each shows `Started ...Application`.

**Ports:** 8081 auth, 8082 ticket, 8083 user, 8084 shop, 8085 technician, 8086 inventory, 8087 marketplace, 8088 pickup, 8089 notification, 8090 subscription, 8091 master-data, 8092 order.

**Test data:** See **services/README-E2E-TEST-DATA.md** for logins and seeded data.

### Option B: Core 3 services only (auth, master-data, ticket)

```powershell
cd C:\Users\baran\repair-shop-saas
.\run-backend-dev.ps1
```

This opens **3 PowerShell windows**:

| Window   | Service        | Port | Wait for log line           |
|----------|----------------|------|-----------------------------|
| 1        | auth-service   | 8081 | `Started AuthServiceApplication` |
| 2        | master-data    | 8091 | `Started MasterDataServiceApplication` |
| 3        | ticket-service | 8082 | `Started TicketServiceApplication` |

**Sample data (all tables):** With profile `dev`, each service seeds data on startup. **auth:** 1 shop "Green Mobiles (E2E)" (slug `admin`), 7 users including `barani`/`barani`, `owner`/`test`, `technician`/`test`, `customer`/`test`. **master-data:** Brands, models, RAM, storage, repair services. **ticket:** 5 sample tickets for the Admin shop (CREATED, IN_PROGRESS, COMPLETED).

**Test credentials:** `barani` / `barani` (SUPER_ADMIN), `owner` / `test`, `technician` / `test`, `customer` / `test`.

If you don’t have Maven, run each service from your IDE: **AuthServiceApplication**, **MasterDataServiceApplication**, **TicketServiceApplication** with VM option: `-Dspring.profiles.active=dev`.

---

## 2. Configure and start the admin panel

**One-time setup:** copy env and set API bases (if not already done):

```powershell
cd C:\Users\baran\repair-shop-admin
copy .env.local.example .env.local
```

Ensure **.env.local** contains (no trailing slashes):

```
NEXT_PUBLIC_API_BASE=http://localhost:8091
NEXT_PUBLIC_AUTH_BASE=http://localhost:8081
NEXT_PUBLIC_TICKET_BASE=http://localhost:8082
NEXT_PUBLIC_SHOP_BASE=http://localhost:8084
NEXT_PUBLIC_SUBSCRIPTION_BASE=http://localhost:8089
```

**Start admin:**

```powershell
cd C:\Users\baran\repair-shop-admin
npm install
npm run dev
```

Open **http://localhost:3000** (or the URL shown). Log in with **barani** / **barani**.

---

## 3. Configure and start the mobile app

### Emulator / same machine (localhost)

Default config already points to `localhost` for auth (8081), master (8091), and ticket (8082). Just start Expo:

```powershell
cd C:\Users\baran\repair-shop-mobile
npm install
npx expo start
```

- Press **a** for Android emulator or **i** for iOS simulator (Mac).
- Or press **w** to open in web (may have CORS/API limitations).

### Physical phone (same Wi‑Fi)

The phone must call your **PC’s IP**, not `localhost`. Set that in **repair-shop-mobile/app.json**:

1. Find your PC’s IPv4 address, e.g. `192.168.1.10` (run `ipconfig` and look at your Wi‑Fi adapter).
2. In **app.json**, under `expo.extra`, set:

```json
"extra": {
  "API_HOST": "192.168.1.10",
  "API_BASE_URL": null,
  "AUTH_BASE": "http://192.168.1.10:8081",
  "MASTER_BASE": "http://192.168.1.10:8091",
  "TICKET_BASE": "http://192.168.1.10:8082",
  ...
}
```

(Replace `192.168.1.10` with your actual IP. Other `*_BASE` can stay `null` so the app uses `API_HOST` for defaults.)

3. Start Expo and scan the QR code with your phone (Camera app → tap “Open in Expo Go”):

```powershell
cd C:\Users\baran\repair-shop-mobile
npx expo start
```

---

## 4. Quick checklist

| Step | Command / action |
|------|-------------------|
| Backend | `cd repair-shop-saas` → `.\run-backend-dev.ps1` → wait for 3 “Started” lines |
| Admin   | `cd repair-shop-admin` → ensure `.env.local` → `npm run dev` → http://localhost:3000 |
| Mobile  | `cd repair-shop-mobile` → (optional) set `API_HOST` in app.json for device → `npx expo start` |

**E2E flow details** (add technician, create/accept ticket): see **services/README-E2E-FLOW.md**.
