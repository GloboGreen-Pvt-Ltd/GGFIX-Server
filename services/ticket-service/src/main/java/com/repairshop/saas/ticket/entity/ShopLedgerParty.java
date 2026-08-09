package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One account the shop counter keeps with a person — a customer it bills or a
 * supplier it buys from. See migration 81.
 *
 * Deliberately not the same row as `customers`: that table means "someone who
 * brought a device in" and is created by a booking, while a supplier never has
 * a ticket at all.
 */
@Entity
@Table(name = "shop_ledger_parties", indexes = {
    @Index(name = "idx_shop_ledger_parties_shop_type", columnList = "shop_id, party_type, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopLedgerParty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    /** CUSTOMER or SUPPLIER — which of the two books this row belongs to. */
    @Column(name = "party_type", length = 10, nullable = false)
    private String partyType;

    /** Never blank: falls back to the phone when the owner saved without a name. */
    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
