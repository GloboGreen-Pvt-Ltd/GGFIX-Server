# End-to-end flow: run all services and test booking + add technician

## 1. Run all backend services (one command)

From repo root:

```powershell
.\run-backend-dev.ps1
```

This opens **3 terminal windows** (auth on 8081, master-data on 8091, ticket on 8082). Wait until each shows `Started ...Application`.

Or run from your IDE: start **AuthServiceApplication**, **MasterDataServiceApplication**, and **TicketServiceApplication** with VM option **`-Dspring.profiles.active=dev`**.

---

## 2. Ports and seed data

| Service        | Port | Seed data |
|----------------|------|-----------|
| auth-service   | 8081 | Shop "Admin" (slug `admin`), user **barani** / **barani** (SUPER_ADMIN) |
| master-data-service | 8091 | Brands, models, RAM, storage, repair services |
| ticket-service | 8082 | (none; create tickets via API) |

---

## 3. E2E flow: add technician to a shop

1. **List shops**  
   `GET http://localhost:8081/auth/shops`  
   Returns the seeded "Admin" shop (note its `id`).

2. **Add a technician**  
   `POST http://localhost:8081/auth/shops/{shopId}/technicians`  
   Body (JSON):
   ```json
   { "email": "tech1@admin.com", "password": "tech123", "name": "Tech One" }
   ```  
   Use the Admin shop `id` from step 1.

3. **Log in as technician**  
   `POST http://localhost:8081/auth/login`  
   Body: `{ "email": "tech1@admin.com", "password": "tech123" }`  
   Response includes `accessToken` and `shopId`. Use this token for ticket APIs.

---

## 4. E2E flow: create booking (ticket) and accept it

Ticket APIs require a **JWT** with `shopId` (from login). Use the token of the **shop owner** (barani) or the **technician** you added.

1. **Log in as shop owner**  
   `POST http://localhost:8081/auth/login`  
   Body: `{ "email": "barani", "password": "barani" }`  
   Copy `accessToken` and `shopId`.

2. **Create a ticket (booking)**  
   `POST http://localhost:8082/tickets`  
   Headers: `Authorization: Bearer <accessToken>`  
   Body (JSON) – `customerId` can be any UUID for now (e.g. a placeholder customer):
   ```json
   {
     "customerId": "00000000-0000-0000-0000-000000000001",
     "brandId": "<uuid from GET /master/brands>",
     "modelId": "<uuid from GET /master/brands/{brandId}/models>",
     "issueDescription": "Screen cracked"
   }
   ```  
   You get a ticket with `id` and `status` (e.g. OPEN).

3. **Accept / update booking status**  
   `PATCH http://localhost:8082/tickets/{ticketId}/status?status=IN_PROGRESS`  
   Headers: `Authorization: Bearer <accessToken>`  
   Other status values depend on your ticket service (e.g. ACCEPTED, IN_PROGRESS, COMPLETED).

---

## 5. Using the admin panel

- Set **`.env.local`** in `repair-shop-admin`:
  - `NEXT_PUBLIC_AUTH_BASE=http://localhost:8081`
  - `NEXT_PUBLIC_API_BASE=http://localhost:8091`
  - `NEXT_PUBLIC_TICKET_BASE=http://localhost:8082` (if the admin calls ticket APIs; add to admin API client if needed)

- Log in with **barani** / **barani**.
- Use **Master Data** for CRUD on brands, models, repair services, RAM, storage.
- **Shops** and **Subscriptions** pages call shop/subscription services; add those services later for full admin flows.
- To support **tickets** in the admin (list/create/accept), the admin app would call `http://localhost:8082/tickets` with the JWT from login (same as auth base for token).

---

## 6. Optional: customer ID for tickets

Ticket creation requires `customerId`. For a full flow you can:

- Add a **customer** user to a shop via a similar endpoint (e.g. POST /auth/shops/{id}/customers), or
- Use a fixed placeholder UUID for testing until you have a user/customer service.

All services use **in-memory H2** with the `dev` profile; data is lost on restart. Switch to PostgreSQL and turn off the dev profile when you need persistence.
