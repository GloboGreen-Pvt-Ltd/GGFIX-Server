package com.repairshop.saas.marketplace.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One step of an enquiry's negotiation timeline. */
@Entity
@Table(name = "marketplace_enquiry_offers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketplaceEnquiryOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "enquiry_id", nullable = false)
    private UUID enquiryId;

    @Column(nullable = false, length = 10)
    private String author; // SHOP | BUYER | SYSTEM

    @Column(nullable = false, length = 20)
    private String action; // ENQUIRY | OFFER | COUNTER | ACCEPT | REJECT | CONFIRM | CANCEL

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
