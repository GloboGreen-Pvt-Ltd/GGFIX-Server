# Repair Shop SaaS — Spring Boot Microservices

Multi-module Maven project. Each service is a Spring Boot app with JWT, REST, Swagger, DTOs, Repository pattern, and global exception handling.

## Quick start

**Prerequisites:** Java 25+, Maven 3.8+, PostgreSQL with schema applied (`database/schema/01_schema.sql`).

### Shared secret (dev)

All services read the same environment variable: **`JWT_SECRET`**. For testing, use the same value everywhere, then replace later.

Copy:

```bash
copy .env.example .env
```

### Start PostgreSQL (recommended)

From `services/`:

```bash
docker compose up -d
```

This starts Postgres and auto-applies the schema from `postgres/init/01_schema.sql`.

```bash
# Build all
mvn clean install -DskipTests

# Run Auth Service (login/register, issues JWT)
cd auth-service && mvn spring-boot:run

# Run Ticket Service (requires JWT in header)
cd ticket-service && mvn spring-boot:run
```

### Run all services (Windows)

From `services/`:

```bash
powershell -ExecutionPolicy Bypass -File .\\scripts\\run-all.ps1 -EnvFile .\\.env
```

**Env (optional):** `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`. Defaults in `application.yml`.

## Services

| Service | Port | Swagger |
|---------|------|---------|
| auth-service | 8081 | http://localhost:8081/swagger-ui.html |
| ticket-service | 8082 | http://localhost:8082/swagger-ui.html |
| user-service | 8083 | — |
| shop-service | 8084 | — |
| technician-service | 8085 | — |
| inventory-service | 8086 | — |
| marketplace-service | 8087 | — |
| pickup-service | 8088 | — |
| notification-service | 8089 | — |
| subscription-service | 8090 | — |
| master-data-service | 8091 | — |
| order-service | 8092 | — |

## Implemented

- **Auth Service:** Login, register, JWT generation, BCrypt, global exception handler, Swagger.
- **Ticket Service:** CRUD tickets, list by status, JWT filter, `shop_id` from token, global exception handler, Swagger.

## Docs

- [Folder structure](docs/FOLDER-STRUCTURE.md)
- [API endpoints & DTOs](docs/API-ENDPOINTS-AND-DTOs.md)

## Example: Login then list tickets

```bash
# 1. Register (or use existing shop)
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{"shopName":"Green Mobiles","shopSlug":"green-mobiles","email":"owner@test.com","password":"password123","name":"Owner"}'

# 2. Login
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"owner@test.com","password":"password123","shopSlug":"green-mobiles"}'
# Copy accessToken from response.

# 3. List tickets (replace TOKEN)
curl -X GET "http://localhost:8082/tickets?page=0&size=10" \
  -H "Authorization: Bearer TOKEN"
```
