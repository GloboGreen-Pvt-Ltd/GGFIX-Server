# E2E test data (all services)

When you run all backends with **profile `dev`** (`.\run-all-backend-dev.ps1`), the following **sample data** is created for end-to-end testing.

---

## 1. Auth service (8081)

**Seeded on startup (AdminSeeder, dev only):**

| Table  | Data |
|--------|------|
| **shops** | 1 shop: **Green Mobiles (E2E)**, slug `admin`, id `00000000-0000-0000-0000-000000000001`, phone, address, timezone |
| **users** | 7 users (all in Admin shop): |

**Test logins (email / password):**

| Role        | Email         | Password | Use for |
|-------------|---------------|----------|---------|
| SUPER_ADMIN | `barani`      | `barani` | Admin panel, full access |
| SHOP_OWNER  | `owner`       | `test`   | Owner flows (dashboard, tickets, reports) |
| TECHNICIAN  | `technician`  | `test`   | Technician flows (assigned tickets, status updates) |
| CUSTOMER    | `customer`    | `test`   | Customer flows (book repair, track, buy) |
| TECHNICIAN  | `tech1@admin.com` | `tech123` | Extra technician |
| CUSTOMER    | `customer@test.com` | `cust123` | Extra customer |

Use these in the **mobile app** and **admin panel** for E2E.

---

## 2. Master Data service (8091)

**Seeded when DB is empty (MasterDataSeeder, dev only):**

| Table / resource   | Sample data |
|--------------------|-------------|
| **Brands**         | Apple, Samsung, Google, OnePlus, Xiaomi |
| **Models**         | Per brand (e.g. iPhone 15/14/13, Galaxy S24/S23, Pixel 8/7, OnePlus 12/11, Xiaomi 14, Redmi Note 13, etc.) |
| **RAM options**   | 2 GB, 4 GB, 6 GB, 8 GB, 12 GB, 16 GB |
| **Storage options**| 32 GB, 64 GB, 128 GB, 256 GB, 512 GB, 1 TB |
| **Repair services**| Screen, Battery, Water Damage, Speaker, Camera, Charging Port, Software/OS, Back Glass |

Use **GET /master/brands**, **GET /master/brands/{id}/models**, etc. for dropdowns and ticket creation.

---

## 3. Ticket service (8082)

**Seeded when DB is empty (TicketSeeder, dev only):**

| Field   | Value |
|---------|--------|
| **Shop**| Same as Auth Admin shop (`00000000-0000-0000-0000-000000000001`) |
| **Tickets** | 5 sample tickets: |

| Tracking ID | Issue              | Status      | Assigned |
|-------------|--------------------|------------|----------|
| CSPEN1001   | Screen cracked     | CREATED    | —        |
| CSPEN1002   | Battery draining   | IN_PROGRESS| E2E tech |
| CSPEN1003   | Charging port      | COMPLETED  | E2E tech |
| CSPEN1004   | Water damage       | CREATED    | —        |
| CSPEN1005   | Camera focus issue | IN_PROGRESS| E2E tech |

Log in as **owner** or **technician** (see above) and call **GET /tickets** (with JWT) to list these. Use **PATCH /tickets/{id}/status** to change status for E2E.

---

## 4. Other services (user 8083, shop 8084, technician 8085, inventory 8086, marketplace 8087, pickup 8088, notification 8089, subscription 8090, order 8092)

- Run with **profile `dev`** and **H2 in-memory** (or no DB for notification) so they start for E2E.
- **No seed data** is added yet; tables are empty or the service is a stub.
- As you implement APIs (inventory, marketplace, order, pickup, technician, etc.), add **CommandLineRunner** seeders (e.g. when `count() == 0`) and document them here.

---

## 5. Port summary (for admin & mobile)

Use these base URLs in **repair-shop-admin** `.env.local` and **repair-shop-mobile** `app.json` / `src/api/config.js`:

| Service     | Port | Env / config (example)        |
|-------------|------|-------------------------------|
| Auth        | 8081 | NEXT_PUBLIC_AUTH_BASE / AUTH_BASE |
| Ticket      | 8082 | NEXT_PUBLIC_TICKET_BASE / TICKET_BASE |
| User        | 8083 | NEXT_PUBLIC_USER_BASE / (user API) |
| Shop        | 8084 | NEXT_PUBLIC_SHOP_BASE / SHOP_BASE |
| Technician  | 8085 | TECHNICIAN_BASE |
| Inventory   | 8086 | INVENTORY_BASE |
| Marketplace| 8087 | MARKETPLACE_BASE |
| Pickup      | 8088 | PICKUP_BASE |
| Notification| 8089 | (notification API) |
| Subscription| 8090 | NEXT_PUBLIC_SUBSCRIPTION_BASE |
| Master Data | 8091 | NEXT_PUBLIC_API_BASE / MASTER_BASE |
| Order       | 8092 | ORDER_BASE |

---

## 6. Quick E2E flow

1. **Start all backends:** `.\run-all-backend-dev.ps1` (from repair-shop-saas).
2. **Admin:** Set `.env.local` with the ports above, run `npm run dev`, open http://localhost:3000, log in as **barani** / **barani**.
3. **Mobile:** Set `API_HOST` (or AUTH_BASE, MASTER_BASE, TICKET_BASE, etc.) to your PC IP if on device; run `npx expo start`, log in as **owner** / **test**, **technician** / **test**, or **customer** / **test**.
4. **Verify:** List tickets (owner/technician), create a ticket (owner), update status (technician), use master data for brands/models in forms.
