# Mobile Repair Shop SaaS Platform — Architecture

Enterprise-grade, multi-tenant SaaS for mobile repair shops (Shopify-like, repair-focused).

## Deliverables

| # | Deliverable | Location |
|---|-------------|----------|
| 1 | **System architecture diagram** | [SYSTEM-ARCHITECTURE.md](docs/SYSTEM-ARCHITECTURE.md) (ASCII) + [ARCHITECTURE-DIAGRAMS.md](docs/ARCHITECTURE-DIAGRAMS.md) (Mermaid) |
| 2 | **Microservice list** | [SYSTEM-ARCHITECTURE.md § 2](docs/SYSTEM-ARCHITECTURE.md#2-microservice-list) |
| 3 | **Communication between services** | [SYSTEM-ARCHITECTURE.md § 3](docs/SYSTEM-ARCHITECTURE.md#3-communication-between-services) |
| 4 | **Deployment architecture** | [SYSTEM-ARCHITECTURE.md § 4](docs/SYSTEM-ARCHITECTURE.md#4-deployment-architecture-docker--kubernetes) + [ARCHITECTURE-DIAGRAMS.md § 3](docs/ARCHITECTURE-DIAGRAMS.md#3-deployment-architecture-kubernetes) |

## Tech Stack

| Layer | Technology |
|-------|------------|
| Mobile App | React Native |
| Admin Web | React / Next.js |
| Backend | Java Spring Boot (Microservices) |
| Database | PostgreSQL |
| Auth | JWT + OAuth2 (Spring Security) |
| API Gateway | Spring Cloud Gateway |
| Service Discovery | Eureka |
| Messaging | Kafka |
| Storage | AWS S3 |
| Deployment | Docker + Kubernetes |

## Platform Modules

1. Repair Ticket System  
2. Technician Management  
3. Customer Repair Tracking  
4. Pickup Scheduling  
5. Buy/Sell Marketplace  
6. Inventory Management  
7. Billing System  
8. Subscription System  
9. Master Data Management  
10. Notifications  

## User Roles

- **Super Admin** — Platform-wide
- **Shop Owner** — Single tenant (shop)
- **Technician** — Assignments, ticket updates
- **Customer** — Own repairs, tracking

## Viewing Mermaid Diagrams

- **GitHub:** Renders `.md` with Mermaid automatically.
- **VS Code:** Install “Mermaid” extension and open [ARCHITECTURE-DIAGRAMS.md](docs/ARCHITECTURE-DIAGRAMS.md).
- **Online:** Copy diagram code into [mermaid.live](https://mermaid.live).
