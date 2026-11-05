package com.repairshop.saas.marketplace.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_chat_threads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerChatThread {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_user_id", nullable = false)
    private UUID customerUserId;

    @Column(name = "shop_id")
    private UUID shopId;

    @Column(length = 255)
    private String subject;

    @Column(name = "last_message_preview", length = 255)
    private String lastMessagePreview;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    // Snapshot of counterpart display info — keeps the inbox query a single-table
    // SELECT so neither customer nor shop side has to call user-/shop-service.
    @Column(name = "customer_name", length = 120)
    private String customerName;

    @Column(name = "customer_mobile", length = 20)
    private String customerMobile;

    @Column(name = "customer_avatar_url", length = 500)
    private String customerAvatarUrl;

    @Column(name = "shop_name", length = 160)
    private String shopName;

    @Column(name = "shop_image_url", length = 500)
    private String shopImageUrl;

    // Unread counters incremented on send, zeroed by mark-read.
    @Column(name = "unread_customer_count", nullable = false)
    @Builder.Default
    private Integer unreadCustomerCount = 0;

    @Column(name = "unread_shop_count", nullable = false)
    @Builder.Default
    private Integer unreadShopCount = 0;

    @Column(name = "customer_last_read_at")
    private Instant customerLastReadAt;

    @Column(name = "shop_last_read_at")
    private Instant shopLastReadAt;

    // Poll-based typing indicator: set to "now + ~5s" while the user is typing.
    @Column(name = "customer_typing_until")
    private Instant customerTypingUntil;

    @Column(name = "shop_typing_until")
    private Instant shopTypingUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (unreadCustomerCount == null) unreadCustomerCount = 0;
        if (unreadShopCount == null) unreadShopCount = 0;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
