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
public class ChatThreadResponse {
    private UUID id;
    private UUID customerUserId;
    private UUID shopId;
    private String subject;
    private String lastMessagePreview;
    private Instant lastMessageAt;

    // Counterpart display info — server-side decides "who is the other party"
    // and folds the right snapshot in so the client can render the row with
    // one field set.
    private String counterpartName;
    private String counterpartAvatarUrl;
    private String counterpartPhone;
    private Boolean counterpartOnline;     // last_seen_at within ~60s
    private Instant counterpartLastSeenAt;
    private Boolean counterpartTyping;     // counterpart's typing_until > now

    // Per-side unread count from this caller's perspective. Both sides share
    // the same response shape — the controller picks which side the caller is.
    private Integer unreadCount;
}
