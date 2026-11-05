package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repair_booking_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlatformRepairBookingEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(nullable = false, length = 100) private String status;
    @Column(columnDefinition = "TEXT") private String note;
    @Column(length = 100) private String actor;
    // Optional media attached to a step event — currently only set by the
    // technician's compliance-note submit, so the customer / owner timelines
    // can render a voice-note + image thumbnails inline on the "Issue
    // Verified & Updated" row without a separate fetch.
    @Column(name = "audio_url", columnDefinition = "TEXT") private String audioUrl;
    @Column(name = "images_json", columnDefinition = "TEXT") private String imagesJson;
    // updatable=true so emitOrUpdateBookingEvent can refresh the timestamp
    // on re-submit. The customer/owner timeline rail uses createdAt to find
    // "the latest action", so a stale value would visually omit a fresh tap.
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
}
