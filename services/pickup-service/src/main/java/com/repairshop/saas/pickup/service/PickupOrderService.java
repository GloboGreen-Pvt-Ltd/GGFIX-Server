package com.repairshop.saas.pickup.service;

import com.repairshop.saas.pickup.dto.PickupOrderEventResponse;
import com.repairshop.saas.pickup.dto.PickupOrderRequest;
import com.repairshop.saas.pickup.dto.PickupOrderResponse;
import com.repairshop.saas.pickup.dto.RescheduleRequest;
import com.repairshop.saas.pickup.entity.CustomerPickupOrder;
import com.repairshop.saas.pickup.entity.CustomerPickupOrderEvent;
import com.repairshop.saas.pickup.exception.ForbiddenException;
import com.repairshop.saas.pickup.exception.ResourceNotFoundException;
import com.repairshop.saas.pickup.repository.CustomerPickupOrderEventRepository;
import com.repairshop.saas.pickup.repository.CustomerPickupOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PickupOrderService {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerPickupOrderRepository pickupOrderRepository;
    private final CustomerPickupOrderEventRepository pickupOrderEventRepository;

    public static String generateOrderNumber() {
        StringBuilder sb = new StringBuilder("#CSPQX");
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        // Spec asked for "#CSPQX{6 random alphanumerics}" but the example "#CSPQX9V898502"
        // shows 8 chars after the prefix. We follow the example length to keep parity.
        return sb.toString();
    }

    @Transactional
    public PickupOrderResponse create(UUID userId, PickupOrderRequest req) {
        String orderNumber = uniqueOrderNumber();
        CustomerPickupOrder order = CustomerPickupOrder.builder()
                .orderNumber(orderNumber)
                .customerUserId(userId)
                .shopId(req.getShopId())
                .ticketId(req.getTicketId())
                .addressId(req.getAddressId())
                .flowType(req.getFlowType() != null ? req.getFlowType() : "REPAIR_PICKUP")
                .pickupDate(req.getPickupDate())
                .pickupSlotStart(req.getPickupSlotStart())
                .pickupSlotEnd(req.getPickupSlotEnd())
                .note(req.getNote())
                .estimateAmount(req.getEstimateAmount())
                .status("ORDER_PLACED")
                .build();
        order = pickupOrderRepository.save(order);

        CustomerPickupOrderEvent ev = CustomerPickupOrderEvent.builder()
                .pickupOrderId(order.getId())
                .status("ORDER_PLACED")
                .note("Order placed")
                .actor("SYSTEM")
                .build();
        pickupOrderEventRepository.save(ev);

        return toResponse(order);
    }

    private String uniqueOrderNumber() {
        for (int i = 0; i < 10; i++) {
            String candidate = generateOrderNumber();
            if (pickupOrderRepository.findByOrderNumber(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate unique order number");
    }

    @Transactional(readOnly = true)
    public List<PickupOrderResponse> listMine(UUID userId, String statusFilter) {
        List<CustomerPickupOrder> orders;
        if (statusFilter == null || statusFilter.isBlank()) {
            orders = pickupOrderRepository.findByCustomerUserIdOrderByCreatedAtDesc(userId);
        } else {
            String upper = statusFilter.toUpperCase();
            switch (upper) {
                case "ACTIVE" -> {
                    // Active = everything not COMPLETED / CANCELLED.
                    List<CustomerPickupOrder> all = pickupOrderRepository.findByCustomerUserIdOrderByCreatedAtDesc(userId);
                    orders = all.stream()
                            .filter(o -> {
                                String s = o.getStatus() == null ? "" : o.getStatus().toUpperCase();
                                return !s.equals("COMPLETED") && !s.equals("CANCELLED");
                            })
                            .toList();
                }
                case "COMPLETED" -> orders = pickupOrderRepository
                        .findByCustomerUserIdAndStatusInOrderByCreatedAtDesc(userId, List.of("COMPLETED"));
                case "CANCELLED" -> orders = pickupOrderRepository
                        .findByCustomerUserIdAndStatusInOrderByCreatedAtDesc(userId, List.of("CANCELLED"));
                default -> orders = pickupOrderRepository
                        .findByCustomerUserIdAndStatusInOrderByCreatedAtDesc(userId, List.of(upper));
            }
        }
        return orders.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PickupOrderResponse getById(UUID userId, UUID id) {
        CustomerPickupOrder order = pickupOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pickup order not found: " + id));
        ensureOwner(userId, order);
        return toResponseWithEvents(order);
    }

    @Transactional(readOnly = true)
    public PickupOrderResponse getByShop(UUID shopId, UUID id) {
        CustomerPickupOrder order = pickupOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pickup order not found: " + id));
        if (order.getShopId() == null || !order.getShopId().equals(shopId)) {
            throw new ForbiddenException("This pickup order does not belong to your shop");
        }
        return toResponseWithEvents(order);
    }

    @Transactional(readOnly = true)
    public List<PickupOrderResponse> listForShop(UUID shopId, String statusFilter) {
        List<CustomerPickupOrder> orders;
        if (statusFilter == null || statusFilter.isBlank()) {
            orders = pickupOrderRepository.findByShopIdOrderByCreatedAtDesc(shopId);
        } else {
            String upper = statusFilter.toUpperCase();
            switch (upper) {
                case "ACTIVE" -> {
                    List<CustomerPickupOrder> all = pickupOrderRepository.findByShopIdOrderByCreatedAtDesc(shopId);
                    orders = all.stream()
                            .filter(o -> {
                                String s = o.getStatus() == null ? "" : o.getStatus().toUpperCase();
                                return !s.equals("COMPLETED") && !s.equals("CANCELLED");
                            })
                            .toList();
                }
                case "COMPLETED" -> orders = pickupOrderRepository
                        .findByShopIdAndStatusInOrderByCreatedAtDesc(shopId, List.of("COMPLETED"));
                case "CANCELLED" -> orders = pickupOrderRepository
                        .findByShopIdAndStatusInOrderByCreatedAtDesc(shopId, List.of("CANCELLED"));
                default -> orders = pickupOrderRepository
                        .findByShopIdAndStatusInOrderByCreatedAtDesc(shopId, List.of(upper));
            }
        }
        return orders.stream().map(this::toResponse).toList();
    }

    @Transactional
    public PickupOrderResponse updateStatus(UUID userId, UUID id, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        CustomerPickupOrder order = pickupOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pickup order not found: " + id));
        ensureOwner(userId, order);

        String newStatus = status.toUpperCase();
        order.setStatus(newStatus);
        pickupOrderRepository.save(order);

        pickupOrderEventRepository.save(CustomerPickupOrderEvent.builder()
                .pickupOrderId(order.getId())
                .status(newStatus)
                .note("Status updated to " + newStatus)
                .actor("USER")
                .build());

        return toResponseWithEvents(order);
    }

    @Transactional
    public PickupOrderResponse reschedule(UUID userId, UUID id, RescheduleRequest req) {
        CustomerPickupOrder order = pickupOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pickup order not found: " + id));
        ensureOwner(userId, order);

        if (req.getPickupDate() != null) order.setPickupDate(req.getPickupDate());
        if (req.getPickupSlotStart() != null) order.setPickupSlotStart(req.getPickupSlotStart());
        if (req.getPickupSlotEnd() != null) order.setPickupSlotEnd(req.getPickupSlotEnd());
        pickupOrderRepository.save(order);

        pickupOrderEventRepository.save(CustomerPickupOrderEvent.builder()
                .pickupOrderId(order.getId())
                .status(order.getStatus())
                .note("Rescheduled")
                .actor("USER")
                .build());

        return toResponseWithEvents(order);
    }

    @Transactional
    public PickupOrderResponse cancel(UUID userId, UUID id) {
        CustomerPickupOrder order = pickupOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pickup order not found: " + id));
        ensureOwner(userId, order);

        order.setStatus("CANCELLED");
        pickupOrderRepository.save(order);

        pickupOrderEventRepository.save(CustomerPickupOrderEvent.builder()
                .pickupOrderId(order.getId())
                .status("CANCELLED")
                .note("Order cancelled by user")
                .actor("USER")
                .build());

        return toResponseWithEvents(order);
    }

    private void ensureOwner(UUID userId, CustomerPickupOrder order) {
        if (!order.getCustomerUserId().equals(userId)) {
            throw new ForbiddenException("You do not have access to this pickup order");
        }
    }

    private PickupOrderResponse toResponse(CustomerPickupOrder o) {
        return PickupOrderResponse.builder()
                .id(o.getId())
                .orderNumber(o.getOrderNumber())
                .customerUserId(o.getCustomerUserId())
                .shopId(o.getShopId())
                .ticketId(o.getTicketId())
                .addressId(o.getAddressId())
                .flowType(o.getFlowType())
                .pickupDate(o.getPickupDate())
                .pickupSlotStart(o.getPickupSlotStart())
                .pickupSlotEnd(o.getPickupSlotEnd())
                .status(o.getStatus())
                .estimateAmount(o.getEstimateAmount())
                .finalAmount(o.getFinalAmount())
                .note(o.getNote())
                .createdAt(o.getCreatedAt())
                .events(List.of())
                .build();
    }

    private PickupOrderResponse toResponseWithEvents(CustomerPickupOrder o) {
        PickupOrderResponse resp = toResponse(o);
        List<PickupOrderEventResponse> events = pickupOrderEventRepository
                .findByPickupOrderIdOrderByCreatedAtAsc(o.getId())
                .stream()
                .map(e -> PickupOrderEventResponse.builder()
                        .id(e.getId())
                        .status(e.getStatus())
                        .note(e.getNote())
                        .actor(e.getActor())
                        .createdAt(e.getCreatedAt())
                        .build())
                .toList();
        resp.setEvents(events);
        return resp;
    }
}
