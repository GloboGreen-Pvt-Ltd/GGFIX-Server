package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Free-text note attached to a ticket. The schema for this table is in
 * {@code 01_schema.sql} (line 198) — only the JPA mapping is new here so
 * the ticket detail screen's "Service Compliance Notes" submit has an
 * endpoint to call.
 */
@Entity
@Table(name = "repair_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepairNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "note", nullable = false, columnDefinition = "TEXT")
    private String note;

    @Column(name = "is_internal")
    private Boolean isInternal;

    @Column(name = "audio_url", columnDefinition = "TEXT")
    private String audioUrl;

    @Column(name = "images_json", columnDefinition = "TEXT")
    private String imagesJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
