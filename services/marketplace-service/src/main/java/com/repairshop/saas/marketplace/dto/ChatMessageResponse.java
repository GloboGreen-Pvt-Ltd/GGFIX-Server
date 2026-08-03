package com.repairshop.saas.marketplace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageResponse {
    private UUID id;
    private UUID threadId;
    private String sender;       // CUSTOMER | SHOP | SYSTEM
    private String body;
    private String attachmentUrl;
    private String attachmentType; // IMAGE | AUDIO | FILE
    private Instant createdAt;
    private Instant readAt;
    private Boolean read;        // convenience: readAt != null
}
