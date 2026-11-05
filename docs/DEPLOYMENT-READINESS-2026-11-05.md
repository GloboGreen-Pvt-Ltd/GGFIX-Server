# Deployment Readiness — Doorstep Device Purchase (2026-11-05)

Branch: `feat/sell-doorstep-purchase` (from `main@56c7554`). Nothing merged, deployed, restarted or applied to any shared database.

**Verdict: code PASS on an isolated database (68/68 end-to-end checks). Not yet cleared for production** — the remaining blockers are release logistics: Maven/CI build on the real toolchain, app releases, database backup and migration approval (§6).

## 1. Baseline

| Item | Finding |
|---|---|
| Production repo | `GloboGreen-Pvt-Ltd/NearGFix-Production-Server`, `main@56c7554` (2026-11-05 16:33 IST) |
| Feature branch | Only the files in §2 differ from `main`. Other remote branches have 0 commits not in `main`. |
| Earlier doorstep code | Never committed (lost when the local folder was refreshed from `main`); re-implemented. Migrations `111`/`112` were recovered from disk. |
| Unrelated features | Repair bookings, repair pickups, staff tasks, marketplace, auth, notifications, subscriptions, media — no file changed. |

## 2. Changes vs `main`

| File | Change |
|---|---|
| `ticket-service/.../SellPickupController.java` | Employee App doorstep flow: 1-hour start rule, pickup PIN (send / verify, 5 tries, 30 s resend), arrival (at PIN verification), inspection start / save (≥3 photos), suggested price, customer answer at the door, payment only after owner approval, collect, hand-over awaiting owner verification. |
| `ticket-service/.../DatabaseSchemaInitializer.java` | Doorstep columns at startup (idempotent). |
| `order-service/.../ShopPurchaseController.java` | Owner: approve quote / approve suggestion / own price / re-inspection; verified receipt (IMEI must match); one stock entry per purchase; read-only `GET /shop-purchases/inventory`; **no reassign or cancel once paid at the door** (fixed during validation). |
| `order-service/.../CustomerSellPurchaseController.java` | Pickup PIN for the customer; accept / reject / negotiate the doorstep offer. |
| `order-service/.../ListingQuotationController.java` | `GET /listing-quotations/mine`. |
| `order-service/.../SellOrderController.java` | Customer can't cancel once paid at the door. |
| `order-service/.../DatabaseSchemaInitializer.java` | Same columns as ticket-service. |
| `services/postgres/init/111_…`, `112_…`, `113_…` | Doorstep columns (all nullable, `pickup_otp_attempts` NOT NULL DEFAULT 0). |
| `deploy/db/2026-11-05_sell_doorstep_PROD.sql` | 111 + 112 + 113 in one transaction. **Not applied.** |
| `scripts/e2e/sell_doorstep_e2e.py` | The end-to-end check used below (isolated DB only). |

## 3. Migration review

| Check | Result |
|---|---|
| 111 → 112 → 113 on a fresh PostgreSQL 16 built from `database/schema` + `services/postgres/init` (branch) | **PASS** — each applies cleanly |
| `2026-11-05_sell_doorstep_PROD.sql` after 111–113, then again | **PASS** — idempotent, no conflict |
| Column types vs startup initializers | **PASS** — identical (`pickup_otp VARCHAR(6)`, `pickup_otp_attempts INTEGER NOT NULL DEFAULT 0`, rest nullable) |
| Data changes / drops | None — additive `ADD COLUMN IF NOT EXISTS` only |

## 4. End-to-end validation — 68/68 PASS

Isolated PostgreSQL 16.15 (scratch cluster, port 55432) + order-service and ticket-service built from the branch, started on test ports; JWTs minted with the dev default secret.

| Area | Checks (all PASS) |
|---|---|
| Permissions | other shop's owner 403 · customer token on Partner API 403 · other employee can't accept · inventory list shop-scoped |
| Journey + OTP | journey blocked >1 h before slot · PIN only in Customer App, never in Employee App · inspection blocked before PIN · 5 wrong PINs → locked · resend rate-limited · new PIN works · arrival recorded · PIN hidden after verification |
| Inspection | start time recorded · payment blocked before inspection · ≥3 photos enforced · Partner sees inspection + decision actions |
| Partner price approval | payment locked until owner approval · re-inspection request forces a new inspection · employee suggestion → owner approve · owner can't change a price the customer is answering |
| Customer decision | Customer App gets Approve / Reject / Negotiate · negotiate · reject · accept → final price · payment locked while negotiating / after rejection · accept idempotent |
| Payment | proof photo required · recorded amount = agreed price · duplicate payment ignored · Partner sees "awaiting customer confirmation" · **reassign, customer cancel and owner cancel blocked after payment** |
| Hand-over + inventory | Employee hand-over leaves the order waiting for the owner · receipt needs order + condition verification · wrong IMEI refused · verified receipt → `PAYMENT_PENDING` + stock ref · second receipt no-op · exactly one stock entry · complete blocked until customer confirms · complete idempotent · inventory list shows the device · quote-badge endpoint |
| Regression: shop visit | receive without body · shop inspection · confirm price · payout · customer confirm · complete with stock ref · two purchases → two stock entries |

**Defects found and fixed during validation:** (1) the owner could reassign a pickup after the employee had paid the customer, which reset the order; (2) the owner could cancel after the doorstep payment. Both are now refused. A reassign before payment clears the previous employee's PIN/arrival.

## 5. App compatibility

| App | Status |
|---|---|
| Customer App | Works unchanged: reads `pickup.pin` and `APPROVE_REVISION` / `REJECT_REVISION`. No button for *negotiate* yet (endpoint ready). |
| Partner App | Needs the new build (doorstep screens, verified receipt). Old builds can't confirm receipt of a pickup (400 — verification required). New build is backward-compatible with the current server. |
| Employee App | New build matches the server (endpoint paths, `OTP_NOT_SENT` / `OTP_LOCKED`, `PRICE_NOT_APPROVED`, `inspection/start`). Old builds work but show the server's "waiting for owner approval" message instead of a waiting screen and don't record "Inspection Started". |

Not exercised: the mobile UIs themselves against this test server (they point at the production API).

## 6. Blockers and risks

1. **Merge = deploy.** `deploy-production-aws-ec2.yml` deploys every service on any push to `main` touching `services/**`.
2. **Real build.** Validation used `javac` + the 2026-11-04 dependency jars; CI must run `mvn -pl services/order-service,services/ticket-service -am clean package` before merge.
3. **In-flight orders.** Pickups already "on the way" have no PIN → employee taps *Generate new PIN*. Collecting now needs PIN + inspection + payment; employee hand-over no longer marks the device received.
4. **Startup DDL.** Both services `ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS …` at boot (existing pattern); short lock, no table rewrite on PG ≥ 11. Apply the PROD script first.
5. **Inventory.** `category = 'DEVICE'`; confirm prod has no CHECK constraint on `inventory_items.category`. inventory-service still has no endpoints; the Inventory page reads from order-service.
6. **Arrival time** = PIN verification time (no GPS "arrived" step).

## 7. Safe deployment order (each step needs approval)

1. Snapshot / back up the production database.
2. Read-only checks: migration 110 present (`assigned_pickup_person_id`, `revision_status`, `payout_recorded_at` on `sell_orders`); no CHECK on `inventory_items.category`.
3. Open a PR `feat/sell-doorstep-purchase` → `main`; CI Maven build + review. **Do not merge yet.**
4. Release the Partner App build, then the Employee App build (both work with the current server).
5. Apply `deploy/db/2026-11-05_sell_doorstep_PROD.sql`; run its verify queries.
6. Merge the PR → GitHub Actions deploys → `deploy/aws/health-check.sh` passes.
7. Production smoke test with one test listing (same path as §4).
8. Rollback: revert the merge commit on `main` (redeploys the previous jars); the added columns can stay.
