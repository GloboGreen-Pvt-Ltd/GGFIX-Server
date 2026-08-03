# Microservices Folder Structure

Each service follows the same layout. **Auth Service** and **Ticket Service** are fully implemented; others are stubs.

## Standard layout per service

```
<service-name>/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/repairshop/saas/<service>/
│   │   │   ├── <Service>Application.java
│   │   │   ├── config/           # SecurityConfig, OpenApiConfig
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── dto/             # Request/Response DTOs
│   │   │   ├── entity/          # JPA entities (if owns DB tables)
│   │   │   ├── exception/       # ApiError, GlobalExceptionHandler, custom exceptions
│   │   │   ├── repository/      # JpaRepository interfaces
│   │   │   ├── security/        # JwtAuthFilter, JwtService (if JWT required)
│   │   │   └── service/         # Business logic
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/...
└── (optional) README.md
```

## Services list

| Service              | Port (default) | Base path   | Description                    |
|----------------------|----------------|------------|--------------------------------|
| auth-service         | 8081           | /auth      | Login, register, JWT           |
| user-service         | 8083           | /users     | Users CRUD per shop            |
| shop-service         | 8084           | /shops     | Shop profile, settings         |
| ticket-service       | 8082           | /tickets   | Repair tickets CRUD            |
| technician-service   | 8085           | /technicians | Technicians, assignments     |
| inventory-service    | 8086           | /inventory | Parts, stock                   |
| marketplace-service  | 8087           | /marketplace | Products, listings           |
| pickup-service       | 8088           | /pickups   | Pickup/delivery scheduling     |
| notification-service | 8089           | /notifications | Send email/SMS/push        |
| subscription-service | 8090           | /subscriptions | Plans, billing period      |
| master-data-service  | 8091           | /master    | Brands, models, RAM, storage   |
| order-service        | 8092           | /orders    | Marketplace orders             |

## Auth service (full) structure

```
auth-service/
├── pom.xml
└── src/main/
    ├── java/.../auth/
    │   ├── AuthServiceApplication.java
    │   ├── config/
    │   │   ├── OpenApiConfig.java
    │   │   └── SecurityConfig.java
    │   ├── controller/
    │   │   └── AuthController.java
    │   ├── dto/
    │   │   ├── LoginRequest.java
    │   │   ├── LoginResponse.java
    │   │   ├── RegisterRequest.java
    │   │   └── RegisterResponse.java
    │   ├── entity/
    │   │   ├── Shop.java
    │   │   └── User.java
    │   ├── exception/
    │   │   ├── ApiError.java
    │   │   ├── BadRequestException.java
    │   │   ├── GlobalExceptionHandler.java
    │   │   └── UnauthorizedException.java
    │   ├── repository/
    │   │   ├── ShopRepository.java
    │   │   └── UserRepository.java
    │   ├── security/
    │   │   └── JwtService.java
    │   └── service/
    │       └── AuthService.java
    └── resources/
        └── application.yml
```

## Ticket service (full) structure

```
ticket-service/
├── pom.xml
└── src/main/
    ├── java/.../ticket/
    │   ├── TicketServiceApplication.java
    │   ├── config/
    │   │   └── SecurityConfig.java
    │   ├── controller/
    │   │   └── TicketController.java
    │   ├── dto/
    │   │   ├── TicketRequest.java
    │   │   └── TicketResponse.java
    │   ├── entity/
    │   │   └── Ticket.java
    │   ├── exception/
    │   │   ├── ApiError.java
    │   │   ├── GlobalExceptionHandler.java
    │   │   └── ResourceNotFoundException.java
    │   ├── repository/
    │   │   └── TicketRepository.java
    │   ├── security/
    │   │   ├── JwtAuthFilter.java
    │   │   └── JwtService.java
    │   └── service/
    │       └── TicketService.java
    └── resources/
        └── application.yml
```
