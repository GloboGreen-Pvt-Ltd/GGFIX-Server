# Architecture Diagrams (Mermaid)

Render these in GitHub, VS Code (Mermaid extension), or [mermaid.live](https://mermaid.live).

---

## 1. System Architecture Diagram

```mermaid
flowchart TB
    subgraph Clients
        MA[Mobile App<br/>React Native]
        WP[Admin Web Panel<br/>React / Next.js]
    end

    subgraph Edge["Edge / API Layer"]
        GW[Spring Cloud API Gateway<br/>Routing, JWT, Rate Limit]
    end

    subgraph Discovery["Service Discovery"]
        EU[Eureka]
    end

    subgraph Microservices["Microservices"]
        AUTH[Auth & Identity]
        REPAIR[Repair Ticket]
        TECH[Technician]
        CUST[Customer & Tracking]
        PICKUP[Pickup Scheduling]
        MKT[Marketplace]
        INV[Inventory]
        BILL[Billing]
        SUB[Subscription]
        MD[Master Data]
        NOTIF[Notification]
    end

    subgraph Data["Data & Storage"]
        PG[(PostgreSQL)]
        S3[(AWS S3)]
    end

    subgraph Messaging["Messaging"]
        KF[Kafka]
    end

    MA -->|HTTPS| GW
    WP -->|HTTPS| GW
    GW --> EU
    GW --> AUTH
    GW --> REPAIR
    GW --> TECH
    GW --> CUST
    GW --> PICKUP
    GW --> MKT
    GW --> INV
    GW --> BILL
    GW --> SUB
    GW --> MD
    GW --> NOTIF

    AUTH --> PG
    REPAIR --> PG
    TECH --> PG
    CUST --> PG
    PICKUP --> PG
    MKT --> PG
    INV --> PG
    BILL --> PG
    SUB --> PG
    MD --> PG
    NOTIF --> PG

    REPAIR --> KF
    BILL --> KF
    SUB --> KF
    NOTIF --> KF
    INV --> KF
    MKT --> KF
    AUTH --> S3
```

---

## 2. Microservice Communication (Sync vs Async)

```mermaid
flowchart LR
    subgraph Sync["Synchronous (REST / Feign)"]
        GW2[API Gateway]
        R2[Repair Ticket]
        T2[Technician]
        C2[Customer]
        M2[Master Data]
        B2[Billing]
        GW2 --> R2
        R2 --> T2
        R2 --> C2
        R2 --> M2
        B2 --> R2
    end

    subgraph Async["Asynchronous (Kafka)"]
        direction TB
        TP[repair-ticket.events]
        TB2[billing.events]
        TS[subscription.events]
        TN[notification.requests]
        R3[Repair Ticket]
        B3[Billing]
        S3[Subscription]
        N3[Notification]
        R3 --> TP
        B3 --> TB2
        S3 --> TS
        TP --> B3
        TP --> N3
        TB2 --> S3
        TB2 --> N3
        TS --> N3
    end
```

---

## 3. Deployment Architecture (Kubernetes)

```mermaid
flowchart TB
    subgraph Internet
        USR[Users]
    end

    subgraph K8s["Kubernetes Cluster"]
        subgraph Ingress
            ING[Ingress / Load Balancer<br/>api.repair-saas.com]
        end

        subgraph NS Gateway["Namespace: api-gateway"]
            GW[Spring Cloud Gateway<br/>Replicas: 2+]
        end

        subgraph NS Eureka["Namespace: discovery"]
            EU[Eureka Server<br/>Port 8761]
        end

        subgraph NS Kafka["Namespace: messaging"]
            KF[Kafka Cluster<br/>Brokers: 3]
        end

        subgraph NS Svcs["Namespace: microservices"]
            AUTH[auth-svc]
            REP[repair-svc]
            TEC[tech-svc]
            CUS[customer-svc]
            PIC[pickup-svc]
            MKT[marketplace-svc]
            INV[inventory-svc]
            BIL[billing-svc]
            SUB[subscription-svc]
            MDS[master-data-svc]
            NOT[notification-svc]
        end

        subgraph NS Data["Namespace: data"]
            PG[(PostgreSQL)]
        end
    end

    subgraph External["External"]
        S3[(AWS S3)]
    end

    USR --> ING
    ING --> GW
    GW --> EU
    GW --> AUTH
    GW --> REP
    GW --> TEC
    GW --> CUS
    GW --> PIC
    GW --> MKT
    GW --> INV
    GW --> BIL
    GW --> SUB
    GW --> MDS
    GW --> NOT
    AUTH --> PG
    REP --> PG
    NOT --> S3
```

---

## 4. Multi-Tenant Request Flow

```mermaid
sequenceDiagram
    participant App as Mobile/Web App
    participant GW as API Gateway
    participant Auth as Auth Service
    participant Svc as Any Microservice
    participant DB as PostgreSQL
    participant K as Kafka

    App->>GW: Request + JWT
    GW->>GW: Validate JWT, extract tenant_id
    GW->>Svc: Forward + X-Tenant-Id header
    Svc->>Svc: Set tenant context (thread-local)
    Svc->>DB: Query with tenant_id filter
    DB-->>Svc: Result
    Svc->>K: Publish event (tenant-id in header)
    Svc-->>GW: Response
    GW-->>App: Response
```

---

## 5. Event-Driven Flow Example (Repair Ticket Closed)

```mermaid
sequenceDiagram
    participant Technician
    participant Repair as Repair Ticket Service
    participant Kafka
    participant Billing as Billing Service
    participant Notif as Notification Service
    participant Customer as Customer Tracking

    Technician->>Repair: PATCH /tickets/{id} status=CLOSED
    Repair->>Repair: Update DB, set delivered
    Repair->>Kafka: repair-ticket.events (TicketClosed)
    Kafka->>Billing: Consume
    Kafka->>Notif: Consume
    Kafka->>Customer: Consume
    Billing->>Billing: Create/finalize invoice
    Notif->>Notif: Send "Device ready" push/email
    Customer->>Customer: Update tracking status
```

---

## 6. User Roles and Scope

```mermaid
flowchart LR
    subgraph Super Admin
        SA[Tenant CRUD<br/>Plans<br/>Audit]
    end

    subgraph Shop Owner
        SO[Shop Config<br/>Technicians<br/>Billing<br/>All Tickets]
    end

    subgraph Technician
        TC[Own Assignments<br/>Update Status<br/>Parts]
    end

    subgraph Customer
        CU[Own Repairs<br/>Tracking<br/>Pickup]
    end

    SA --> SO
    SO --> TC
    SO --> CU
```
