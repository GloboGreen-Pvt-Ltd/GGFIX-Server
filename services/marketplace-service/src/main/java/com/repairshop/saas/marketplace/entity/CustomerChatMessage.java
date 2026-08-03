package com.repairshop.saas.marketplace.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    @Column(nullable = false, length = 50)
    private String sender; // CUSTOMER | SHOP | SYSTEM

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    // IMAGE | AUDIO | FILE — drives the bubble renderer on both sides.
    @Column(name = "attachment_type", length = 20)
    private String attachmentType;

    // Set the first time the counterpart marks the thread as read.
    // Drives WhatsApp's blue double-tick.
    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
