# Deployment Readiness — Doorstep Device Purchase (2026-11-05)

**Verdict: NOT READY for production yet.** Code compiles and API contracts match, but no integration run was possible (no local database or services), and the release must go out together with Partner App and Employee App updates. Nothing has been deployed, no database was touched and no service was restarted.

## 1. What the local `server monday` folder is

| Item | Finding |
|---|---|
| Production repo | `GloboGreen-Pvt-Ltd/NearGFix-Production-Server`, branch `main` at `56c7554` (2026-11-05 16:33 IST, "fix(media): … cdn.neargfix.com") |
| Local folder | Identical to `main@56c7554` (line endings ignored) **plus** the changes in §2. It was overwritten from `main` at 16:33 IST. |
| Other remote branches | `production-server-branch-05-11-2026`, `fix/neargfix-media-bucket` — 0 commits not already in `main`. |
| Missing commits | None from GitHub. The earlier doorstep implementation (written locally 2026-11-04/05) was **never committed** — only its migrations `111`/`112` survived on disk. It was re-implemented; behaviour may differ from what was tried on devices before. |
| Local-only, do not commit | `.github/modernize/` (tooling), `deploy/db/2026-11-04_sell_purchase_workflow_PROD.sql` (migration 110 — confirm it was applied to prod). |

Unrelated production features (repair bookings, repair pickups, staff tasks, marketplace, auth, notifications, subscriptions, media) are **unchanged** — no other file differs from `main`.

## 2. Changes vs `main` (to go in one PR)

| File | Change |
|---|---|
| `ticket-service/.../controller/SellPickupController.java` | Doorstep flow for the Employee App: 1-hour start rule, pickup PIN (send / verify, 5 tries, 30 s resend), arrival time (at PIN verification), inspection start / save (≥3 photos), suggested price, customer answer at the door, payment with proof (**only after owner approval**), collect, hand-over (owner verifies). |
| `ticket-service/.../config/DatabaseSchemaInitializer.java` | Adds the doorstep columns at startup (idempotent). |
| `order-service/.../controller/ShopPurchaseController.java` | Owner: approve quote / approve suggested price / set own price / request re-inspection; verified receipt (IMEI must match the doorstep inspection); one stock entry per purchase; doorstep details in the response; read-only `GET /shop-purchases/inventory`. |
| `order-service/.../controller/CustomerSellPurchaseController.java` | Customer App: pickup PIN on the order page; accept / reject the doorstep final offer; `revision/negotiate`. |
| `order-service/.../controller/ListingQuotationController.java` | `GET /listing-quotations/mine` (quote badges on Buy Home). |
| `order-service/.../controller/SellOrderController.java` | Customer can't cancel once paid at the door. |
| `order-service/.../config/DatabaseSchemaInitializer.java` | Same columns as ticket-service. |
| `services/postgres/init/111_…`, `112_…` | Recovered doorstep migrations (were on disk only). |
| `services/postgres/init/113_sell_doorstep_handover.sql` | New: arrival / inspection-start / hand-over / stock-entry columns. |
| `deploy/db/2026-11-05_sell_doorstep_PROD.sql` | 111 + 112 + 113 in one transaction for prod. **Not applied.** |

## 3. Workflow verification (code trace)

| Step | Who / endpoint | Guard | Result |
|---|---|---|---|
| Start journey | Employee `PATCH sell-pickups/{id}/status PICKUP_ON_THE_WAY` | accepted; not earlier than 1 h before slot | `OUT_FOR_PICKUP`, new 4-digit PIN (shown only in Customer App) |
| OTP | Employee `otp/verify` | PIN match, ≤5 tries | `otpVerifiedAt`, `arrivedAt` |
| Inspection | Employee `inspection/start`, `inspection` | PIN verified, not paid, price not with owner/customer | `inspectionStartedAt`, `inspectedAt`; owner notified |
| Partner price approval | Owner `pickup-price/confirm` · `pickup-revision/approve` · `pickup-price/revise` · `pickup-reinspect` | on the way, inspected, unpaid, price not with customer | `SHOP_CONFIRMED` · `SHOP_APPROVED` · `SHOP_APPROVED` (owner price) · `REINSPECT` |
| Customer acceptance | Customer `revision/approve` / `reject` / `negotiate` (or employee at the door) | `SHOP_APPROVED`, unpaid | `APPROVED` (final price set) / `REJECTED` / `NEGOTIATION` |
| Payment | Employee `payment` | price `SHOP_CONFIRMED` or `APPROVED`; proof URL | payment recorded (never marked paid by Partner) |
| Collect | Employee status `DEVICE_PICKED_UP` | PIN + inspection + payment | `PICKED_UP` |
| Hand-over | Employee `RECEIVED_AT_SHOP` → owner `receive {imei, orderVerified, conditionVerified}` | IMEI equals inspected IMEI; once only | `PAYMENT_PENDING`, stock entry `BUY-<sell no>` |
| Customer confirms payment → complete | Customer `payout/confirm` → owner `complete` | `PAID`, received | `COMPLETED`; stock entry ensured (no duplicate) |

Shop visit stays a separate path (no employee steps).

## 4. Checks run

| Check | Result |
|---|---|
| Full-module `javac` — order-service (52 files), ticket-service (129 files) | **Pass** (exit 0) |
| API contract: every Employee / Partner / Customer App call ↔ server mapping | **Pass** (all mapped) |
| Employee App PIN length (4) and error codes (`OTP_LOCKED`, `OTP_NOT_SENT`) | **Pass** |
| Partner App JS parse + Expo bundle | **Pass** (HTTP 200) |
| Integration / end-to-end against a database | **NOT RUN — blocker** (no local Postgres, Docker, Maven wrapper or running services; prod must not be used) |

## 5. Risks

1. **Merge = deploy.** `deploy-production-aws-ec2.yml` deploys every service on any push to `main` touching `services/**`.
2. **Behaviour changes for in-flight orders.** Pickups already "on the way" have no PIN → the employee taps *Generate new PIN*. Collecting now needs PIN + inspection + payment. Employee *Handed over* no longer marks the device received — the owner must confirm in the Partner App.
3. **App versions.** Old Partner App builds can't confirm receipt of a pickup (the IMEI check needs the new screen). Old Employee App builds work but don't record "Inspection Started", and they show the server's "waiting for owner approval" message instead of a waiting screen. The Customer App needs no update; *negotiate* has no Customer App button yet.
4. **Startup DDL.** Both services run `ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS …` at boot (existing pattern). `pickup_otp_attempts INTEGER NOT NULL DEFAULT 0` takes a short exclusive lock (no rewrite on PG ≥ 11).
5. **Inventory.** The stock entry uses `inventory_items` (guarded by a column check; skipped if the table/columns are missing). `category = 'DEVICE'` — confirm prod has no CHECK constraint on `category`. inventory-service still has no endpoints; the Inventory page reads from order-service until it does.
6. **Arrival time** = PIN verification time (the Employee App has no separate GPS "arrived" step).
7. **PIN storage** is plain text in `sell_orders.pickup_otp` (4 digits, 5 tries, regenerate ≥30 s) — same as the earlier implementation.

## 6. Exact deployment order (each step needs approval)

1. Snapshot / back up the production database.
2. Read-only check that migration 110 is applied: `SELECT column_name FROM information_schema.columns WHERE table_name='sell_orders' AND column_name IN ('assigned_pickup_person_id','revision_status','payout_recorded_at');`
3. Create branch `feat/doorstep-purchase` from `main`, commit only the files in §2, open a PR (do **not** push to `main`).
4. Build with Maven: `mvn -pl services/order-service,services/ticket-service -am clean package`, then run the §3 flow on a staging / disposable database.
5. Release the Partner App update, then the Employee App update (both work with the current server).
6. Apply `deploy/db/2026-11-05_sell_doorstep_PROD.sql`; run its verify queries.
7. Merge the PR → GitHub Actions deploys → confirm `deploy/aws/health-check.sh` passes.
8. Production smoke test with a test listing: quote → accept → assign → start → PIN → inspection → approve / revise → customer accepts → pay → collect → hand-over → confirm receipt (stock ref shown) → customer confirms → complete.
9. Rollback: revert the merge commit on `main` (redeploys the previous jars). The added columns are nullable / defaulted and can stay.
