# Entity-Relationship Diagram — Mobile Repair Shop SaaS

All entities use **UUID** primary keys and **multi-tenant** `shop_id` where applicable. Master data tables are platform-wide (no `shop_id`).

---

## 1. Full ER Diagram (Mermaid)

```mermaid
erDiagram
    shops ||--o{ users : "has"
    shops ||--o{ technicians : "has"
    shops ||--o{ customers : "has"
    shops ||--o| subscriptions : "has"
    shops ||--o{ tickets : "has"
    shops ||--o{ pickup_requests : "has"
    shops ||--o{ inventory_items : "has"
    shops ||--o{ marketplace_products : "has"
    shops ||--o{ marketplace_orders : "has"
    shops ||--o{ sales_history : "has"

    users ||--o{ technicians : "may be"
    users ||--o{ ticket_status_history : "changed by"
    users ||--o{ repair_notes : "authored by"

    customers ||--o{ tickets : "has"
    customers ||--o{ pickup_requests : "for"
    customers ||--o{ marketplace_orders : "placed by"

    technicians ||--o{ tickets : "assigned to"

    master_brands ||--o{ master_models : "has"
    master_brands ||--o{ tickets : "device brand"
    master_brands ||--o{ marketplace_products : "product brand"
    master_models ||--o{ tickets : "device model"
    master_models ||--o{ marketplace_products : "product model"
    master_ram_options ||--o{ tickets : "device ram"
    master_storage_options ||--o{ tickets : "device storage"

    tickets ||--o{ ticket_status_history : "has"
    tickets ||--o{ repair_notes : "has"
    tickets ||--o{ pickup_requests : "linked to"

    marketplace_orders ||--o{ marketplace_order_items : "contains"
    marketplace_products ||--o{ marketplace_order_items : "ordered as"

    shops {
        uuid id PK
        varchar name
        varchar slug UK
        varchar email
        varchar phone
        text address
        timestamptz created_at
        timestamptz updated_at
    }

    users {
        uuid id PK
        uuid shop_id FK
        varchar email
        varchar password_hash
        varchar role
        timestamptz created_at
        timestamptz updated_at
    }

    technicians {
        uuid id PK
        uuid shop_id FK
        uuid user_id FK
        varchar name
        boolean is_available
        timestamptz created_at
        timestamptz updated_at
    }

    customers {
        uuid id PK
        uuid shop_id FK
        varchar name
        varchar phone
        timestamptz created_at
        timestamptz updated_at
    }

    subscriptions {
        uuid id PK
        uuid shop_id FK
        varchar plan_code
        varchar status
        timestamptz current_period_end
        timestamptz created_at
        timestamptz updated_at
    }

    master_brands {
        uuid id PK
        varchar name UK
        timestamptz created_at
        timestamptz updated_at
    }

    master_models {
        uuid id PK
        uuid brand_id FK
        varchar name
        timestamptz created_at
        timestamptz updated_at
    }

    master_ram_options {
        uuid id PK
        int value_gb UK
        varchar label
        timestamptz created_at
        timestamptz updated_at
    }

    master_storage_options {
        uuid id PK
        int value_gb UK
        varchar label
        timestamptz created_at
        timestamptz updated_at
    }

    master_repair_services {
        uuid id PK
        varchar code UK
        varchar name
        timestamptz created_at
        timestamptz updated_at
    }

    tickets {
        uuid id PK
        uuid shop_id FK
        uuid customer_id FK
        uuid assigned_technician_id FK
        varchar tracking_id
        uuid brand_id FK
        uuid model_id FK
        uuid ram_option_id FK
        uuid storage_option_id FK
        varchar status
        decimal estimated_price
        decimal final_price
        timestamptz created_at
        timestamptz updated_at
    }

    ticket_status_history {
        uuid id PK
        uuid ticket_id FK
        varchar from_status
        varchar to_status
        uuid changed_by_id FK
        timestamptz created_at
    }

    repair_notes {
        uuid id PK
        uuid ticket_id FK
        uuid author_id FK
        text note
        boolean is_internal
        timestamptz created_at
        timestamptz updated_at
    }

    pickup_requests {
        uuid id PK
        uuid shop_id FK
        uuid ticket_id FK
        uuid customer_id FK
        varchar type
        timestamptz scheduled_slot
        varchar status
        timestamptz created_at
        timestamptz updated_at
    }

    inventory_items {
        uuid id PK
        uuid shop_id FK
        varchar sku
        varchar name
        varchar category
        int quantity
        decimal unit_price
        timestamptz created_at
        timestamptz updated_at
    }

    marketplace_products {
        uuid id PK
        uuid shop_id FK
        uuid brand_id FK
        uuid model_id FK
        varchar title
        varchar type
        decimal price
        varchar status
        timestamptz created_at
        timestamptz updated_at
    }

    marketplace_orders {
        uuid id PK
        uuid shop_id FK
        uuid customer_id FK
        varchar order_number
        varchar status
        decimal total_amount
        timestamptz created_at
        timestamptz updated_at
    }

    marketplace_order_items {
        uuid id PK
        uuid order_id FK
        uuid product_id FK
        int quantity
        decimal unit_price
        timestamptz created_at
        timestamptz updated_at
    }

    sales_history {
        uuid id PK
        uuid shop_id FK
        varchar source
        uuid source_id
        decimal amount
        date sale_date
        timestamptz created_at
        timestamptz updated_at
    }
```

---

## 2. Simplified Diagram by Domain

```mermaid
flowchart TB
    subgraph Tenant["Tenant & Identity"]
        shops
        users
        subscriptions
    end

    subgraph People["People (per shop)"]
        technicians
        customers
    end

    subgraph Master["Master Data (platform)"]
        master_brands
        master_models
        master_ram_options
        master_storage_options
        master_repair_services
    end

    subgraph Repair["Repair Flow"]
        tickets
        ticket_status_history
        repair_notes
        pickup_requests
    end

    subgraph Commerce["Commerce"]
        inventory_items
        marketplace_products
        marketplace_orders
        marketplace_order_items
        sales_history
    end

    shops --> users
    shops --> technicians
    shops --> customers
    shops --> subscriptions
    shops --> tickets
    shops --> pickup_requests
    shops --> inventory_items
    shops --> marketplace_products
    shops --> marketplace_orders
    shops --> sales_history
    users --> technicians
    customers --> tickets
    customers --> pickup_requests
    technicians --> tickets
    master_brands --> master_models
    master_brands --> tickets
    master_models --> tickets
    master_ram_options --> tickets
    master_storage_options --> tickets
    tickets --> ticket_status_history
    tickets --> repair_notes
    tickets --> pickup_requests
    marketplace_orders --> marketplace_order_items
    marketplace_products --> marketplace_order_items
```

---

Render in GitHub, VS Code (Mermaid extension), or [mermaid.live](https://mermaid.live).
