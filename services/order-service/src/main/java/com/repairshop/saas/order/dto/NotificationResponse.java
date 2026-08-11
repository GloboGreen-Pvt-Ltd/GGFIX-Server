package com.repairshop.saas.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponse {
    private UUID id;
    private UUID bookingId;
    private String bookingNumber;
    // Shop feed only — the timeline screen the owner app opens on tap is keyed
    // by ticket id. Null on the customer feed and on pre-migration-86 rows.
    private UUID ticketId;
    private String statusKey;
    private String title;
    private String body;
    private String type;
    private boolean read;
    private Instant createdAt;
}
