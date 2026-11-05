package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One movement of money between the shop and one named party — see migration 82.
 *
 * RECEIVED is money handed to the shop, GIVEN is money handed to the party.
 * The party's balance is derived from these rows and never stored: this is money
 * a shop argues with a customer about, so a figure that can go stale is worse
 * than one that costs a GROUP BY.
 */
@Entity
@Table(name = "shop_ledger_entries", indexes = {
    @Index(name = "idx_shop_ledger_entries_party", columnList = "shop_id, party_id, entry_date"),
    @Index(name = "idx_shop_ledger_entries_shop_date", columnList = "shop_id, entry_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    /**
     * Plain UUID rather than a @ManyToOne. The account is always already loaded
     * (it is what the caller asked for), so a mapping would only add a lazy
     * proxy and an N+1 on the list query that reads these in bulk.
     */
    @Column(name = "party_id", nullable = false)
    private UUID partyId;

    /** RECEIVED (money in from the party) or GIVEN (money out to the party). */
    @Column(name = "direction", length = 10, nullable = false)
    private String direction;

    @Column(name = "amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal amount;

    /** The counter day this belongs to — a local calendar day, not an instant. */
    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "note", length = 500)
    private String note;

    /**
     * A spoken note, recorded at the counter instead of typed — see migration 83.
     * Independent of {@link #note}: an entry can carry both, either or neither.
     */
    @Column(name = "note_audio_url", columnDefinition = "TEXT")
    private String noteAudioUrl;

    /**
     * Photographed bills, as a JSON array of URLs — {@code ["https://…", …]}.
     * Same storage as repair_notes.images_json so both surfaces parse the same
     * shape; null when there are none, never an empty array.
     */
    @Column(name = "bill_images_json", columnDefinition = "TEXT")
    private String billImagesJson;

    /**
     * The repair this money was paid against, when it was one — see migration 84.
     * Plain UUID, not a @ManyToOne: the statement lists many entries and a lazy
     * proxy per row would be an N+1 into tickets on every render.
     */
    @Column(name = "ticket_id")
    private UUID ticketId;

    /** Snapshot of the ticket's label, so the statement reads without a join. */
    @Column(name = "ticket_tracking_id", length = 50)
    private String ticketTrackingId;

    @Column(name = "ticket_label", columnDefinition = "TEXT")
    private String ticketLabel;

    @Column(name = "created_by")
    private UUID createdBy;

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
