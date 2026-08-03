# Mobile Repair Shop SaaS Platform — System Architecture

**Document Version:** 1.0  
**Role:** Senior Enterprise Software Architect  
**Scope:** Multi-tenant SaaS for mobile repair shops (Shopify-like, repair-focused)

---

## 1. High-Level System Architecture Diagram

```
                                    ┌─────────────────────────────────────────────────────────────────┐
                                    │                        CLIENTS                                     │
                                    │  ┌──────────────────┐         ┌──────────────────────────────┐  │
                                    │  │  Mobile App       │         │  Admin Web Panel             │  │
                                    │  │  (React Native)   │         │  (React / Next.js)           │  │
                                    │  │  • Shop Owner     │         │  • Super Admin                │  │
                                    │  │  • Technician     │         │  • Shop Owner                 │  │
                                    │  │  • Customer       │         │  • Reporting & Config         │  │
                                    │  └────────┬─────────┘         └──────────────┬───────────────┘  │
                                    └───────────┼──────────────────────────────────┼───────────────────┘
                                                │                                  │
                                                │ HTTPS                             │ HTTPS
                                                ▼                                  ▼
┌───────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    EDGE / API LAYER                                                          │
│  ┌─────────────────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │                         Spring Cloud API Gateway                                                        │  │
│  │  • Routing (/repair/*, /billing/*, /subscription/*, …)                                                  │  │
│  │  • Rate limiting, CORS, SSL termination                                                                │  │
│  │  • JWT validation (OAuth2 Resource Server)                                                             │  │
│  └─────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                                │
                        ┌───────────────────────┼───────────────────────┐
                        │                       │                       │
                        ▼                       ▼                       ▼
┌───────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              SERVICE DISCOVERY (Eureka)                                                     │
│                    All microservices register; Gateway discovers instances by service name                   │
└───────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┬───────────────┬───────────────┬───────────────┬───────────────┐
        ▼               ▼               ▼               ▼               ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Auth &       │ │ Repair       │ │ Technician   │ │ Customer     │ │ Pickup       │ │ Marketplace  │ │ Inventory    │
│ Identity     │ │ Ticket       │ │ Management   │ │ & Tracking   │ │ Scheduling   │ │ (Buy/Sell)   │ │ Management   │
│ Service      │ │ Service      │ │ Service      │ │ Service      │ │ Service      │ │ Service      │ │ Service      │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
       │                │                │                │                │                │                │
       │                └────────────────┴────────────────┴────────────────┴────────────────┴────────────────┘
       │                                         │
       │                                         ▼
       │                ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
       │                │ Billing      │ │ Subscription │ │ Master Data  │ │ Notification │
       │                │ Service     │ │ Service      │ │ Service      │ │ Service      │  ──────► Kafka
       │                └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
       │
       ▼
┌──────────────┐     ┌──────────────┐
│ PostgreSQL   │     │ AWS S3       │
│ (per-tenant  │     │ (docs,       │
│  or shared   │     │  images)     │
│  schemas)    │     └──────────────┘
└──────────────┘
```

---

## 2. Microservice List

| # | Microservice | Responsibility | Primary DB | Events (Kafka) |
|---|--------------|----------------|------------|-----------------|
| 1 | **Auth & Identity Service** | Login, JWT/OAuth2, user registration, tenant context, RBAC | PostgreSQL | User created, login events |
| 2 | **Repair Ticket Service** | Create/update repair tickets, status workflow, device info, estimates | PostgreSQL | Ticket created/updated/closed |
| 3 | **Technician Management Service** | Technicians CRUD, skills, assignments, availability | PostgreSQL | Technician assigned, availability changed |
| 4 | **Customer & Tracking Service** | Customer profile, repair history, tracking (customer-facing status) | PostgreSQL | Customer created, repair linked |
| 5 | **Pickup Scheduling Service** | Pickup/delivery slots, scheduling, courier or in-shop | PostgreSQL | Pickup scheduled, completed |
| 6 | **Marketplace Service (Buy/Sell)** | Listings, orders, offers for device buy/sell | PostgreSQL | Order placed, listing updated |
| 7 | **Inventory Management Service** | Parts, accessories, stock levels, reorder | PostgreSQL | Stock low, part consumed |
| 8 | **Billing Service** | Invoices, payments, payment gateway integration | PostgreSQL | Invoice generated, payment received |
| 9 | **Subscription Service** | Plans, tenant subscription, usage, upgrades/downgrades | PostgreSQL | Subscription changed, usage metered |
| 10 | **Master Data Service** | Tenants (shops), brands, device models, categories, pricing templates | PostgreSQL | Tenant created, catalog updated |
| 11 | **Notification Service** | Email, SMS, push (FCM/APNs), in-app; consumes events from Kafka | PostgreSQL (preferences) | — (consumer only) |

---

## 3. Communication Between Services

### 3.1 Synchronous (REST / Spring Cloud OpenFeign)

- **API Gateway → Services:** All client traffic is REST. Gateway routes by path and forwards validated JWT (e.g. `X-Tenant-Id`, `X-User-Id`).
- **Service-to-service:** Used when immediate response is required:
  - **Repair Ticket → Technician Service:** Assign technician, get availability.
  - **Repair Ticket → Customer Service:** Get customer details, link repair.
  - **Repair Ticket → Master Data:** Resolve device model, category, default pricing.
  - **Billing → Repair Ticket / Marketplace:** Fetch ticket or order details for invoice lines.
  - **Any service → Auth/Identity:** Validate token or resolve user/tenant (optional; can be done at Gateway).

### 3.2 Asynchronous (Kafka — Event-Driven)

| Event Topic | Producer(s) | Consumer(s) | Purpose |
|-------------|------------|-------------|---------|
| `tenant.events` | Auth, Master Data | Subscription, Billing, Notification | Tenant created/updated; drive onboarding, notifications |
| `repair-ticket.events` | Repair Ticket | Billing, Notification, Customer Tracking | Ticket created/updated/closed → invoice draft, notify customer |
| `technician.events` | Technician | Repair Ticket, Notification | Assignment, availability → update UI, notify technician |
| `pickup.events` | Pickup Scheduling | Notification, Customer Tracking | Slot booked/completed → SMS/email, tracking update |
| `marketplace.events` | Marketplace | Billing, Inventory, Notification | Order placed → invoice, reserve stock, notify |
| `inventory.events` | Inventory | Notification, Master Data | Low stock, part consumed → reorder alerts, reporting |
| `billing.events` | Billing | Subscription, Notification | Payment received → update subscription usage, receipt |
| `subscription.events` | Subscription | Auth, Notification | Plan change, trial end → enforce limits, notify owner |
| `notification.requests` | All services | Notification | Generic “send email/SMS/push” commands |

### 3.3 Multi-Tenancy and Context

- **Tenant isolation:** Every request carries `tenant_id` (from JWT or derived from user). Stored in thread-local or reactive context and used for:
  - **DB:** Tenant-scoped queries (row-level or schema-per-tenant).
  - **Kafka:** Message headers `tenant-id`, `user-id` for filtering and auditing.
- **Service-to-service:** Feign requests (or RestTemplate) forward `X-Tenant-Id`, `Authorization` so downstream services enforce same tenant.

---

## 4. Deployment Architecture (Docker + Kubernetes)

```
                                    ┌─────────────────────────────────────────────────────────────────┐
                                    │                     Kubernetes Cluster                            │
                                    │  ┌─────────────────────────────────────────────────────────────┐  │
                                    │  │ Ingress (e.g. NGINX / AWS ALB Ingress Controller)            │  │
                                    │  │ • Host: api.repair-saas.com, admin.repair-saas.com            │  │
                                    │  │ • TLS termination                                                                 │  │
                                    │  └─────────────────────────────────────────────────────────────┘  │
                                    └─────────────────────────────────────────────────────────────────┘
                                                                    │
        ┌───────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────┐
        │                                                           ▼                                                           │
        │  ┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐  │
        │  │                                    Namespace: api-gateway                                                         │  │
        │  │  ┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────┐  │
        │  │  │ Spring Cloud Gateway (Deployment)  │  Replicas: 2+  │  Service: api-gateway  │  Port: 8080                  │  │
        │  │  └─────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
        │  └─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
        │                                                           │                                                           │
        │  ┌────────────────────────────────────────────────────────┼────────────────────────────────────────────────────────┐  │
        │  │ Namespace: discovery                                    │                                                         │  │
        │  │  ┌──────────────────────────────────────────────────────▼──────────────────────────────────────────────────────┐  │
        │  │  │ Eureka Server (Deployment)  │  Replicas: 1 (or 2 for HA)  │  Service: eureka  │  Port: 8761                   │  │
        │  │  └─────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
        │  └──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
        │                                                           │                                                           │
        │  ┌────────────────────────────────────────────────────────┼────────────────────────────────────────────────────────┐  │
        │  │ Namespace: messaging                                    │                                                         │  │
        │  │  ┌──────────────────────────────────────────────────────▼──────────────────────────────────────────────────────┐  │
        │  │  │ Kafka (StatefulSet)  │  Brokers: 3  │  Zookeeper / KRaft  │  Services: kafka-bootstrap, kafka-0..2           │  │
        │  │  └─────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
        │  └──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
        │                                                           │                                                           │
        │  ┌────────────────────────────────────────────────────────┼────────────────────────────────────────────────────────┐  │
        │  │ Namespace: microservices                                │                                                         │  │
        │  │  Each microservice: Deployment + Service (ClusterIP). Gateway resolves via Eureka (service name).                  │  │
        │  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐        │  │
        │  │  │ auth-svc   │ │ repair-svc │ │ tech-svc   │ │ customer-  │ │ pickup-svc │ │ marketplace│ │ inventory- │ ...     │  │
        │  │  │ (2 replicas)│ │ (3 replicas)│ │ (2 replicas)│ │ svc       │ │ (2 replicas)│ │ -svc      │ │ svc        │        │  │
        │  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘ └────────────┘ └────────────┘ └────────────┘        │  │
        │  └──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
        │                                                           │                                                           │
        │  ┌────────────────────────────────────────────────────────┼────────────────────────────────────────────────────────┐  │
        │  │ Namespace: data (or external)                           │                                                         │  │
        │  │  PostgreSQL: StatefulSet or managed (RDS/Aurora). Per-env DB or schema-per-tenant.                                │  │
        │  │  ┌──────────────────────────────────────────────────────▼──────────────────────────────────────────────────────┐  │
        │  │  │ PostgreSQL  │  Primary + Replicas (read scaling)  │  Service: postgres  │  Port: 5432                         │  │
        │  │  └─────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
        │  └──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
        │                                                           │                                                           │
        └───────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────┘
                                                                    │
                                    ┌───────────────────────────────┴───────────────────────────────┐
                                    │ External: AWS S3 (documents, device images, exports)          │
                                    │ IAM / service accounts for each service that needs S3 access   │
                                    └───────────────────────────────────────────────────────────────┘
```

### 4.1 Key Deployment Conventions

| Concern | Choice |
|--------|--------|
| **Containers** | Each microservice = one Docker image (e.g. `repair-svc:1.0.0`). Base: Eclipse Temurin 21 or 17. |
| **Config** | ConfigMaps for non-secret config; Secrets for DB URLs, JWT keys, OAuth client credentials. |
| **Scaling** | HPA on CPU/memory for Gateway and high-traffic services (e.g. Repair, Billing). |
| **Discovery** | Services register with Eureka; Gateway uses `lb://service-name` to call instances. |
| **Storage** | PostgreSQL PVC or external RDS. S3 for binaries; no S3 inside cluster. |
| **Ingress** | Single host or path-based routing to API Gateway; separate host for Admin (Next.js) if served from cluster. |

---

## 5. Role-Based Access Control (RBAC)

| Role | Scope | Typical Permissions |
|------|--------|----------------------|
| **Super Admin** | Platform-wide | Tenant CRUD, plan management, global config, support tools, audit logs. |
| **Shop Owner** | Single tenant (shop) | Full shop config, technicians, billing, subscription, reports, repair tickets (all). |
| **Technician** | Single tenant | Own assignments, update ticket status, parts consumption, availability. |
| **Customer** | Self + own repairs | View own repairs, track status, book pickup, marketplace (own orders). |

- **Auth & Identity Service** stores roles and tenant membership; JWT includes `roles[]`, `tenant_id`, `tenant_type`.
- **API Gateway / Resource Servers** enforce RBAC per route (e.g. `/api/repair/tickets` → Shop Owner or Technician; `/api/admin/tenants` → Super Admin only).

---

## 6. Module-to-Service Mapping

| Platform Module | Primary Microservice(s) | Supporting |
|------------------|--------------------------|------------|
| 1. Repair Ticket System | Repair Ticket Service | Technician, Customer, Master Data, Billing, Notification |
| 2. Technician Management | Technician Management Service | Repair Ticket, Notification |
| 3. Customer Repair Tracking | Customer & Tracking Service, Repair Ticket | Notification, Pickup |
| 4. Pickup Scheduling | Pickup Scheduling Service | Repair Ticket, Customer, Notification |
| 5. Buy/Sell Marketplace | Marketplace Service | Billing, Inventory, Notification |
| 6. Inventory Management | Inventory Management Service | Repair Ticket, Marketplace, Notification |
| 7. Billing System | Billing Service | Repair Ticket, Marketplace, Subscription |
| 8. Subscription System | Subscription Service | Auth, Billing, Notification |
| 9. Master Data Management | Master Data Service | All domain services |
| 10. Notifications | Notification Service | Consumes Kafka from all |

---

## 7. Document Summary

- **System:** Clients (React Native + Next.js) → API Gateway → Eureka → Spring Boot microservices; Kafka for events; PostgreSQL + S3.
- **Microservices:** 11 (Auth, Repair, Technician, Customer, Pickup, Marketplace, Inventory, Billing, Subscription, Master Data, Notification).
- **Communication:** REST/Feign for synchronous; Kafka for event-driven flows and notifications.
- **Deployment:** Docker images per service; Kubernetes with Ingress, Eureka, Kafka, and PostgreSQL; S3 external.

This architecture supports multi-tenancy, RBAC, scalability, and event-driven design as required.
