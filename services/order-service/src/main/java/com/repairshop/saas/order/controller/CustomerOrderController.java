package com.repairshop.saas.order.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repairshop.saas.order.dto.CustomerOrderResponse;
import com.repairshop.saas.order.entity.CustomerNotification;
import com.repairshop.saas.order.entity.CustomerOrder;
import com.repairshop.saas.order.entity.RepairBookingEvent;
import com.repairshop.saas.order.exception.ForbiddenException;
import com.repairshop.saas.order.exception.ResourceNotFoundException;
import com.repairshop.saas.order.repository.CustomerNotificationRepository;
import com.repairshop.saas.order.repository.CustomerOrderRepository;
import com.repairshop.saas.order.repository.RepairBookingEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/customer-orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final CustomerOrderRepository repo;
    private final CustomerNotificationRepository notificationRepo;
    private final RepairBookingEventRepository eventRepo;
    private final ObjectMapper objectMapper;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    // Repair-flow order types whose live status is sourced from the booking
    // timeline rather than customer_orders.status. Keep in sync with the
    // TAB_MAP in MyOrdersScreen.js — the Sell / Buy tabs do not have timelines.
    private static final java.util.Set<String> TIMELINE_ORDER_TYPES =
            java.util.Set.of("REPAIR", "PICKUP", "ENQUIRY");

    // Mirror of SHOP_BOOKING_STATUS_OPTIONS in
    // repair-shop-mobile/src/screens/common/serviceHistoryPhases.js. Backend
    // is the source of truth for the label so the My Orders card matches the
    // History timeline row that's currently marked "NOW".
    private static final java.util.Map<String, String> PHASE_LABELS;
    static {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        m.put("BOOKING_CREATED_BY_SHOP",                       "Booking Created by Shop");
        m.put("SERVICE_ACCEPTED",                              "Service Accepted");
        m.put("ASSIGNED_TO_TECHNICIAN",                        "Assigned to Technician");
        m.put("AWAITING_TECHNICIAN_ACCEPTANCE",                "Awaiting Technician Acceptance");
        m.put("REASSIGNED_TO_TECHNICIAN",                      "Re-Assigned to Technician");
        m.put("TECHNICIAN_ACCEPTED_SERVICE",                   "Technician Accepted Service");
        m.put("TECHNICIAN_WORK_STARTED",                       "Technician Work Started");
        m.put("TECHNICIAN_UPLOADED_DEVICE_IMAGES",             "Technician Uploaded Device Images");
        m.put("TECHNICIAN_COMPLIANCE_ISSUE_VERIFIED_UPDATED",  "Technician Issue Verified & Updated");
        m.put("RE_ESTIMATED_CONFIRMED",                        "Service Re-estimated");
        m.put("CUSTOMER_APPROVED",                             "Customer Approved");
        m.put("CUSTOMER_REJECTED",                             "Customer Rejected");
        m.put("IN_REPAIR",                                     "Repair Work In Progress");
        m.put("PARTS_REQUIRED",                                "Spare Parts Waiting");
        m.put("PARTS_REPLACED",                                "Spare Parts Replaced");
        m.put("QUALITY_CHECK_STARTED",                         "Quality Check Started");
        m.put("QUALITY_CHECK_COMPLETED",                       "Quality Check Completed");
        m.put("REPAIR_COMPLETED",                              "Repair Completed");
        m.put("READY",                                         "Ready for Delivery");
        m.put("RETURN_DELIVERY",                               "Return Delivery");
        m.put("DELIVERED",                                     "Delivered to Customer");
        m.put("CANCELLED",                                     "Repair Cancelled");
        m.put("PICKUP_BOOKING_CREATED",                        "Pickup Booking Created");
        // Pickup-flow keys emitted by /confirm-order and /assign-pickup. Kept
        // in this map so the My Orders Pickup tab also gets a human label.
        m.put("PICKUP_REQUESTED",                            "Pickup Requested");
        m.put("PICKUP_ACCEPTED",                             "Pickup Accepted");
        m.put("PICKUP_PERSON_ASSIGNED",                      "Pickup person assigned");
        m.put("ORDER_SERVICE_CONFIRMED",                       "Service Accepted");
        m.put("PICKUP_ASSIGNED",                               "Pickup person assigned");
        m.put("PICKUP_REASSIGNED",                             "Pickup person reassigned");
        m.put("PICKUP_ON_THE_WAY",                             "Pickup person on the way");
        m.put("REPAIR_ESTIMATE_PROCESSING",                    "Repair estimate processing");
        m.put("ESTIMATE_SUBMITTED",                            "Estimate submitted");
        m.put("DEVICE_PICKED_UP",                              "Device picked up");
        m.put("PICKED_UP",                                     "Device picked up");
        m.put("REACHED_SHOP",                                  "Reached shop");
        m.put("ESTIMATE_SENT_TO_CUSTOMER",                     "Estimate sent to customer");
        m.put("REPAIR_IN_PROGRESS",                            "Repair in progress");
        m.put("READY_FOR_DELIVERY",                            "Ready for delivery");
        m.put("ORDER_PLACED",                                  "Booking Placed");
        PHASE_LABELS = java.util.Collections.unmodifiableMap(m);
    }

    @GetMapping
    public ResponseEntity<List<CustomerOrderResponse>> list(
            HttpServletRequest req,
            @RequestParam(value = "orderType", required = false) String orderType,
            @RequestParam(value = "status", required = false) String status
    ) {
        UUID userId = callerId(req);
        List<CustomerOrder> orders = orderType == null || orderType.isBlank()
                ? repo.findByCustomerUserIdOrderByCreatedAtDesc(userId)
                : repo.findByCustomerUserIdAndOrderTypeOrderByCreatedAtDesc(userId, orderType.toUpperCase());
        if (status != null && !status.isBlank()) {
            String upper = status.toUpperCase();
            orders = orders.stream().filter(o -> matchesStatus(upper, o.getStatus())).toList();
        }
        return ResponseEntity.ok(orders.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerOrderResponse> get(HttpServletRequest req, @PathVariable UUID id) {
        UUID userId = callerId(req);
        CustomerOrder o = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!o.getCustomerUserId().equals(userId)) throw new ForbiddenException("Not your order");
        return ResponseEntity.ok(toResponse(o));
    }

    /**
     * Checkout: turn the customer's cart (sent from the app) into a BUY order
     * so it shows in the My Orders "Buy" tab, and notify the customer. The cart
     * itself is cleared by the app via the marketplace service after this call.
     * Body: { items: [{ productId, title, price, quantity }], totalAmount }.
     */
    @PostMapping("/buy")
    @Transactional
    public ResponseEntity<CustomerOrderResponse> buy(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        UUID userId = callerId(req);
        Object itemsObj = body.get("items");
        if (!(itemsObj instanceof List<?> items) || items.isEmpty()) {
            throw new IllegalArgumentException("items required");
        }
        BigDecimal total;
        try {
            Object t = body.get("totalAmount");
            total = t == null ? BigDecimal.ZERO : new BigDecimal(t.toString());
        } catch (NumberFormatException e) {
            total = BigDecimal.ZERO;
        }
        String title;
        if (items.size() == 1 && items.get(0) instanceof Map<?, ?> first && first.get("title") != null) {
            title = String.valueOf(first.get("title"));
        } else {
            title = items.size() == 1 ? "Item" : items.size() + " items";
        }

        String orderNumber = uniqueOrderNumber();
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("items", items);
        String payloadJson;
        try { payloadJson = objectMapper.writeValueAsString(payload); }
        catch (Exception e) { payloadJson = null; }

        CustomerOrder o = repo.save(CustomerOrder.builder()
                .orderNumber(orderNumber)
                .customerUserId(userId)
                .orderType("BUY")
                .status("PENDING")
                .totalAmount(total)
                .payloadJson(payloadJson)
                .build());

        notificationRepo.save(CustomerNotification.builder()
                .customerUserId(userId)
                .bookingNumber(orderNumber)
                .statusKey("ORDER_PLACED")
                .title("Order placed")
                .body("Your order " + orderNumber + " has been placed.")
                .type("orders")
                .read(false)
                .build());

        return ResponseEntity.ok(toResponse(o));
    }

    private String uniqueOrderNumber() {
        String n;
        do {
            StringBuilder sb = new StringBuilder("#B");
            for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            n = sb.toString();
        } while (repo.findByOrderNumber(n).isPresent());
        return n;
    }

    private boolean matchesStatus(String filter, String orderStatus) {
        if (orderStatus == null) return false;
        String s = orderStatus.toUpperCase();
        return switch (filter) {
            case "PENDING" -> !s.equals("COMPLETED") && !s.equals("CANCELLED");
            case "COMPLETED" -> s.equals("COMPLETED");
            case "CANCELLED" -> s.equals("CANCELLED");
            default -> s.equals(filter);
        };
    }

    private CustomerOrderResponse toResponse(CustomerOrder o) {
        Map<String, Object> payload = null;
        if (o.getPayloadJson() != null && !o.getPayloadJson().isBlank()) {
            try { payload = objectMapper.readValue(o.getPayloadJson(), new TypeReference<Map<String, Object>>() {}); }
            catch (Exception ignored) { payload = Map.of("raw", o.getPayloadJson()); }
        }
        String phaseStatus = null;
        String phaseLabel = null;
        if (o.getReferenceId() != null
                && o.getOrderType() != null
                && TIMELINE_ORDER_TYPES.contains(o.getOrderType().toUpperCase())) {
            RepairBookingEvent latest = eventRepo
                    .findFirstByBookingIdOrderByCreatedAtDesc(o.getReferenceId())
                    .orElse(null);
            if (latest != null && latest.getStatus() != null) {
                phaseStatus = latest.getStatus().toUpperCase();
                String mapped = PHASE_LABELS.get(phaseStatus);
                phaseLabel = mapped != null ? mapped
                        : (latest.getNote() != null && !latest.getNote().isBlank()
                                ? latest.getNote()
                                : phaseStatus.replace('_', ' '));
            }
        }
        return CustomerOrderResponse.builder()
                .id(o.getId()).orderNumber(o.getOrderNumber()).shopId(o.getShopId())
                .orderType(o.getOrderType()).referenceId(o.getReferenceId())
                .status(o.getStatus())
                .phaseStatus(phaseStatus).phaseLabel(phaseLabel)
                .totalAmount(o.getTotalAmount())
                .payload(payload).createdAt(o.getCreatedAt()).updatedAt(o.getUpdatedAt())
                .build();
    }

    private UUID callerId(HttpServletRequest req) {
        requireRole(req, "CUSTOMER");
        Object u = req.getAttribute("userId");
        if (u == null) throw new ForbiddenException("Missing userId");
        return UUID.fromString(u.toString());
    }

    private void requireRole(HttpServletRequest req, String role) {
        Object raw = req.getAttribute("roles");
        if (raw instanceof List<?> roles && roles.stream().anyMatch(r -> role.equalsIgnoreCase(String.valueOf(r)))) {
            return;
        }
        throw new ForbiddenException("Role not allowed");
    }
}
