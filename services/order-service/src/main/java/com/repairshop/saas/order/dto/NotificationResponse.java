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
    private String statusKey;
    private String title;
    private String body;
    private String type;
    private boolean read;
    private Instant createdAt;
}
