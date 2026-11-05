# Database Relationships — Mobile Repair Shop SaaS

Multi-tenant design: **shop_id** is the tenant key. All tenant-scoped tables reference **shops**. Master data has no **shop_id** (shared catalog).

---

## 1. Tenant & Identity

| Table | Role | Key relationships |
|-------|------|--------------------|
| **shops** | Root tenant entity. One row per mobile repair shop. | Referenced by users, technicians, customers, subscriptions, tickets, pickup_requests, inventory_items, marketplace_*, sales_history. |
| **users** | Shop staff (owner, technician login). Belongs to one shop. | **shop_id → shops**. Optional link to **technicians** via user_id. Authors **repair_notes** and **ticket_status_history** (changed_by_id). |
| **subscriptions** | One active subscription per shop (plan, billing period). | **shop_id → shops** (UNIQUE). Used by Billing/Subscription microservices. |

- **Multi-tenant:** Every tenant-scoped table has `shop_id`; queries filter by `shop_id` (from JWT/context).

---

## 2. People (per shop)

| Table | Role | Key relationships |
|-------|------|--------------------|
| **technicians** | Shop technicians; may have a login (user_id). | **shop_id → shops**. **user_id → users** (nullable). Referenced by **tickets.assigned_technician_id**. |
| **customers** | Shop’s customers (repair and/or marketplace). | **shop_id → shops**. Referenced by **tickets**, **pickup_requests**, **marketplace_orders**. |

- Customers and technicians are isolated per shop; same person in two shops = two rows.

---

## 3. Master Data (platform-wide, no shop_id)

| Table | Role | Key relationships |
|-------|------|--------------------|
| **master_brands** | Device/brand catalog (e.g. Samsung, Apple). | Referenced by **master_models**, **tickets** (device brand), **marketplace_products**. |
| **master_models** | Models under a brand (e.g. Galaxy S24). | **brand_id → master_brands**. Referenced by **tickets**, **marketplace_products**. |
| **master_ram_options** | RAM options (4 GB, 8 GB, …). | Referenced by **tickets** (device RAM). |
| **master_storage_options** | Storage options (128 GB, 256 GB, …). | Referenced by **tickets** (device storage). |
| **master_repair_services** | Standard repair types (e.g. screen, battery). | Referenced by application logic / ticket line items (can add junction table later). |

- Shared across all shops; optional future extension: shop-specific overrides with nullable **shop_id**.

---

## 4. Repair Flow

| Table | Role | Key relationships |
|-------|------|--------------------|
| **tickets** | Repair job. One per device/repair request. | **shop_id → shops**. **customer_id → customers**. **assigned_technician_id → technicians**. **brand_id, model_id, ram_option_id, storage_option_id** → master_* (device). **tracking_id** unique per shop. |
| **ticket_status_history** | Audit trail of status changes. | **ticket_id → tickets** (CASCADE delete). **changed_by_id → users** (nullable). |
| **repair_notes** | Notes on a ticket (internal or customer-visible). | **ticket_id → tickets**. **author_id → users** (nullable). |
| **pickup_requests** | Pickup or delivery request. | **shop_id → shops**. **ticket_id → tickets** (nullable; can exist without ticket). **customer_id → customers**. |

- Lifecycle: Ticket created → status updates (history) → notes added → pickup/delivery (pickup_requests). All scoped by **shop_id** via **tickets** or directly.

---

## 5. Commerce (Inventory & Marketplace)

| Table | Role | Key relationships |
|-------|------|--------------------|
| **inventory_items** | Parts/accessories/consumables per shop. | **shop_id → shops**. **sku** unique per shop. |
| **marketplace_products** | Buy/sell listings. | **shop_id → shops**. **brand_id, model_id** → master_* (optional). Referenced by **marketplace_order_items**. |
| **marketplace_orders** | Customer order (basket). | **shop_id → shops**. **customer_id → customers** (nullable for guest). **order_number** unique per shop. |
| **marketplace_order_items** | Line items of an order. | **order_id → marketplace_orders**. **product_id → marketplace_products**. |
| **sales_history** | Denormalized sales record for billing/reporting. | **shop_id → shops**. **source** = 'REPAIR' | 'MARKETPLACE'; **source_id** = ticket_id or order_id. |

- Marketplace: **marketplace_products** (shop’s listings) → **marketplace_orders** + **marketplace_order_items** (orders and lines). **sales_history** can be fed by repair tickets and marketplace orders (event-driven).

---

## 6. Foreign Key Summary

| Child table | Foreign key(s) | Parent | On delete |
|-------------|----------------|--------|-----------|
| users | shop_id | shops | CASCADE |
| technicians | shop_id, user_id | shops, users | CASCADE, SET NULL |
| customers | shop_id | shops | CASCADE |
| subscriptions | shop_id | shops | CASCADE |
| tickets | shop_id, customer_id, assigned_technician_id, brand_id, model_id, ram_option_id, storage_option_id | shops, customers, technicians, master_* | CASCADE, RESTRICT, SET NULL, SET NULL, SET NULL, SET NULL |
| ticket_status_history | ticket_id, changed_by_id | tickets, users | CASCADE, SET NULL |
| repair_notes | ticket_id, author_id | tickets, users | CASCADE, SET NULL |
| pickup_requests | shop_id, ticket_id, customer_id | shops, tickets, customers | CASCADE, SET NULL, RESTRICT |
| inventory_items | shop_id | shops | CASCADE |
| marketplace_products | shop_id, brand_id, model_id | shops, master_brands, master_models | CASCADE, SET NULL, SET NULL |
| marketplace_orders | shop_id, customer_id | shops, customers | CASCADE, SET NULL |
| marketplace_order_items | order_id, product_id | marketplace_orders, marketplace_products | CASCADE, RESTRICT |
| sales_history | shop_id | shops | CASCADE |
| master_models | brand_id | master_brands | CASCADE |

---

## 7. Multi-Tenant Query Pattern

- **Resolve tenant:** From JWT (or session): `tenant_id` = `shop_id`.
- **All reads/writes:** Add `WHERE shop_id = :tenant_id` (or equivalent) for: users, technicians, customers, tickets, pickup_requests, inventory_items, marketplace_*, sales_history.
- **Joins:** When joining to master_* or to other tenant tables, always constrain by the same `shop_id` so rows from other tenants are never exposed.

This keeps data isolated per shop and supports the SaaS multi-tenant architecture.
