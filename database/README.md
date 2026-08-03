# Database — Mobile Repair Shop SaaS

PostgreSQL schema for the multi-tenant SaaS platform (UUID PKs, `created_at`/`updated_at`, `shop_id` for tenant isolation).

## Contents

| Item | Location |
|------|----------|
| **1. ER diagram** | [docs/ER-DIAGRAM.md](docs/ER-DIAGRAM.md) (Mermaid) |
| **2. SQL table definitions** | [schema/01_schema.sql](schema/01_schema.sql) |
| **3. Relationship explanation** | [docs/RELATIONSHIPS.md](docs/RELATIONSHIPS.md) |

## Tables

### Tenant & identity
- **shops** — Tenant root (one per repair shop)
- **users** — Shop staff (owner, technician login); `shop_id` FK
- **subscriptions** — One per shop (plan, period)

### People (per shop)
- **technicians** — Technicians; optional `user_id` for login
- **customers** — Shop customers

### Master data (platform-wide)
- **master_brands**, **master_models**, **master_ram_options**, **master_storage_options**, **master_repair_services**

### Repair
- **tickets** — Repair jobs; links customer, technician, device (master_*)
- **ticket_status_history** — Status audit
- **repair_notes** — Notes on tickets
- **pickup_requests** — Pickup/delivery; optional `ticket_id`

### Commerce
- **inventory_items** — Parts/accessories per shop
- **marketplace_products** — Buy/sell listings
- **marketplace_orders**, **marketplace_order_items**
- **sales_history** — Sales audit (source: REPAIR or MARKETPLACE)

## Run schema

```bash
psql -U postgres -d your_database -f schema/01_schema.sql
```

Requires PostgreSQL 10+ and extension `pgcrypto` (for `gen_random_uuid()`).
