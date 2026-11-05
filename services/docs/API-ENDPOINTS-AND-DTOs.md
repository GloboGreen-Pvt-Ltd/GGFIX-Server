# API Endpoints & DTO Models

All secured endpoints require header: `Authorization: Bearer <JWT>`.
Multi-tenant: `shop_id` is taken from JWT; no need to pass in path for tenant-scoped resources.

---

## 1. Auth Service (port 8081)

| Method | Path | Description |
|--------|------|-------------|
| POST | /auth/login | Login; returns JWT + user/shop info |
| POST | /auth/register | Register new shop + owner |

### DTOs

- **LoginRequest:** `email` (required), `password` (required), `shopSlug` (optional)
- **LoginResponse:** `accessToken`, `tokenType`, `expiresIn`, `userId`, `shopId`, `email`, `name`, `roles`
- **RegisterRequest:** `shopName`, `shopSlug`, `email`, `password`, `name`
- **RegisterResponse:** `userId`, `shopId`, `shopSlug`, `email`, `message`

---

## 2. User Service (port 8083)

| Method | Path | Description |
|--------|------|-------------|
| GET | /users | List users for shop (paginated) |
| GET | /users/{id} | Get user by ID |
| POST | /users | Create user (invite) |
| PUT | /users/{id} | Update user |
| DELETE | /users/{id} | Deactivate user |

### DTOs

- **UserRequest:** `email`, `name`, `role`, `password` (on create)
- **UserResponse:** `id`, `shopId`, `email`, `name`, `role`, `isActive`, `createdAt`, `updatedAt`

---

## 3. Shop Service (port 8084)

| Method | Path | Description |
|--------|------|-------------|
| GET | /shops/me | Get current shop (from JWT) |
| PUT | /shops/me | Update shop profile |
| GET | /shops/{id} | Get shop by ID (admin or same shop) |

### DTOs

- **ShopRequest:** `name`, `email`, `phone`, `address`, `timezone`
- **ShopResponse:** `id`, `name`, `slug`, `email`, `phone`, `address`, `timezone`, `isActive`, `createdAt`, `updatedAt`

---

## 4. Ticket Service (port 8082) — implemented

| Method | Path | Description |
|--------|------|-------------|
| GET | /tickets | List tickets (optional ?status=), paginated |
| GET | /tickets/{id} | Get ticket by ID |
| POST | /tickets | Create ticket |
| PUT | /tickets/{id} | Update ticket |
| PATCH | /tickets/{id}/status | Update status (?status=CREATED|IN_REPAIR|...) |

### DTOs

- **TicketRequest:** `customerId`, `brandId`, `modelId`, `ramOptionId`, `storageOptionId`, `color`, `imei`, `issueDescription`, `estimatedPrice`
- **TicketResponse:** `id`, `shopId`, `customerId`, `assignedTechnicianId`, `trackingId`, `brandId`, `modelId`, `color`, `status`, `estimatedPrice`, `finalPrice`, `issueDescription`, `createdAt`, `updatedAt`

---

## 5. Technician Service (port 8085)

| Method | Path | Description |
|--------|------|-------------|
| GET | /technicians | List technicians for shop |
| GET | /technicians/{id} | Get technician by ID |
| POST | /technicians | Create technician |
| PUT | /technicians/{id} | Update technician |
| PATCH | /technicians/{id}/availability | Set is_available |
| GET | /technicians/{id}/assignments | List tickets assigned |

### DTOs

- **TechnicianRequest:** `name`, `email`, `phone`, `roleLabel`, `userId` (optional)
- **TechnicianResponse:** `id`, `shopId`, `userId`, `name`, `email`, `phone`, `roleLabel`, `isAvailable`, `createdAt`, `updatedAt`

---

## 6. Inventory Service (port 8086)

| Method | Path | Description |
|--------|------|-------------|
| GET | /inventory/items | List items (optional ?category=) |
| GET | /inventory/items/{id} | Get item by ID |
| POST | /inventory/items | Create item |
| PUT | /inventory/items/{id} | Update item |
| PATCH | /inventory/items/{id}/stock | Adjust quantity (?delta= or ?quantity=) |

### DTOs

- **InventoryItemRequest:** `sku`, `name`, `category`, `quantity`, `unitPrice`, `reorderLevel`
- **InventoryItemResponse:** `id`, `shopId`, `sku`, `name`, `category`, `quantity`, `unitPrice`, `reorderLevel`, `createdAt`, `updatedAt`

---

## 7. Marketplace Service (port 8087)

| Method | Path | Description |
|--------|------|-------------|
| GET | /marketplace/products | List products (?type=SELL|BUY, ?status=) |
| GET | /marketplace/products/{id} | Get product |
| POST | /marketplace/products | Create listing |
| PUT | /marketplace/products/{id} | Update product |
| PATCH | /marketplace/products/{id}/status | Set status (ACTIVE, SOLD, ...) |

### DTOs

- **ProductRequest:** `brandId`, `modelId`, `title`, `description`, `type`, `price`
- **ProductResponse:** `id`, `shopId`, `brandId`, `modelId`, `title`, `description`, `type`, `price`, `status`, `createdAt`, `updatedAt`

---

## 8. Pickup Service (port 8088)

| Method | Path | Description |
|--------|------|-------------|
| GET | /pickups | List pickup requests (?status=) |
| GET | /pickups/{id} | Get pickup by ID |
| POST | /pickups | Create pickup/delivery request |
| PATCH | /pickups/{id}/status | Update status (SCHEDULED, COMPLETED, ...) |

### DTOs

- **PickupRequest:** `ticketId`, `customerId`, `type` (PICKUP|DELIVERY), `scheduledSlot`, `address`
- **PickupResponse:** `id`, `shopId`, `ticketId`, `customerId`, `type`, `scheduledSlot`, `address`, `status`, `completedAt`, `createdAt`, `updatedAt`

---

## 9. Notification Service (port 8089)

| Method | Path | Description |
|--------|------|-------------|
| POST | /notifications/send | Send notification (email/SMS/push) |
| GET | /notifications/preferences | Get user/shop preferences |
| PUT | /notifications/preferences | Update preferences |

### DTOs

- **SendNotificationRequest:** `channel` (EMAIL|SMS|PUSH), `to`, `subject`, `body`, `templateCode`
- **NotificationPreferencesResponse:** `emailEnabled`, `smsEnabled`, `pushEnabled`, ...

---

## 10. Subscription Service (port 8090)

| Method | Path | Description |
|--------|------|-------------|
| GET | /subscriptions/me | Current subscription for shop |
| GET | /subscriptions/plans | List available plans |
| POST | /subscriptions/change-plan | Change plan (upgrade/downgrade) |

### DTOs

- **SubscriptionResponse:** `id`, `shopId`, `planCode`, `status`, `startedAt`, `currentPeriodEnd`, `createdAt`, `updatedAt`
- **PlanResponse:** `code`, `name`, `price`, `features`

---

## 11. Master Data Service (port 8091)

| Method | Path | Description |
|--------|------|-------------|
| GET | /master/brands | List brands |
| GET | /master/brands/{id}/models | List models by brand |
| GET | /master/ram-options | List RAM options |
| GET | /master/storage-options | List storage options |
| GET | /master/repair-services | List repair service types |

### DTOs

- **BrandResponse:** `id`, `name`, `createdAt`, `updatedAt`
- **ModelResponse:** `id`, `brandId`, `name`, `createdAt`, `updatedAt`
- **RamOptionResponse:** `id`, `valueGb`, `label`
- **StorageOptionResponse:** `id`, `valueGb`, `label`
- **RepairServiceResponse:** `id`, `code`, `name`, `description`

---

## 12. Order Service (port 8092)

| Method | Path | Description |
|--------|------|-------------|
| GET | /orders | List orders for shop (paginated) |
| GET | /orders/{id} | Get order with items |
| POST | /orders | Create order (from marketplace) |
| PATCH | /orders/{id}/status | Update order status |

### DTOs

- **OrderRequest:** `customerId`, `items` (productId, quantity, unitPrice)
- **OrderResponse:** `id`, `shopId`, `customerId`, `orderNumber`, `status`, `totalAmount`, `createdAt`, `updatedAt`
- **OrderItemResponse:** `id`, `orderId`, `productId`, `quantity`, `unitPrice`

---

## Global exception response (all services)

```json
{
  "timestamp": "2025-01-01T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/tickets",
  "fieldErrors": [
    { "field": "customerId", "message": "must not be null" }
  ]
}
```

- **401 Unauthorized:** Missing or invalid JWT.
- **403 Forbidden:** Valid JWT but insufficient role.
- **404 Not Found:** Resource not found (e.g. ResourceNotFoundException).
- **400 Bad Request:** Validation errors (fieldErrors) or business rule (message).
