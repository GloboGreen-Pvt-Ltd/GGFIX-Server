package com.repairshop.saas.order.controller;

import com.repairshop.saas.order.dto.NotificationResponse;
import com.repairshop.saas.order.entity.CustomerNotification;
import com.repairshop.saas.order.exception.ForbiddenException;
import com.repairshop.saas.order.exception.ResourceNotFoundException;
import com.repairshop.saas.order.repository.CustomerNotificationRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final CustomerNotificationRepository repo;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list(HttpServletRequest req) {
        UUID userId = callerId(req);
        return ResponseEntity.ok(
                repo.findByCustomerUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(HttpServletRequest req) {
        UUID userId = callerId(req);
        return ResponseEntity.ok(Map.of("count", repo.countByCustomerUserIdAndReadFalse(userId)));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<NotificationResponse> markRead(HttpServletRequest req, @PathVariable UUID id) {
        UUID userId = callerId(req);
        CustomerNotification n = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!n.getCustomerUserId().equals(userId)) throw new ForbiddenException("Not your notification");
        n.setRead(true);
        repo.save(n);
        return ResponseEntity.ok(toResponse(n));
    }

    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<Map<String, Integer>> markAllRead(HttpServletRequest req) {
        UUID userId = callerId(req);
        List<CustomerNotification> all = repo.findByCustomerUserIdOrderByCreatedAtDesc(userId);
        int n = 0;
        for (CustomerNotification x : all) {
            if (!x.isRead()) { x.setRead(true); n++; }
        }
        repo.saveAll(all);
        return ResponseEntity.ok(Map.of("updated", n));
    }

    private NotificationResponse toResponse(CustomerNotification n) {
        return NotificationResponse.builder()
                .id(n.getId()).bookingId(n.getBookingId()).bookingNumber(n.getBookingNumber())
                .statusKey(n.getStatusKey()).title(n.getTitle()).body(n.getBody())
                .type(n.getType()).read(n.isRead()).createdAt(n.getCreatedAt())
                .build();
    }

    private UUID callerId(HttpServletRequest req) {
        Object u = req.getAttribute("userId");
        if (u == null) throw new ForbiddenException("Missing userId");
        return UUID.fromString(u.toString());
    }
}
