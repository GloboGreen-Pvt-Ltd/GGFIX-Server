package com.repairshop.saas.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sell_order_screening_answers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SellOrderScreeningAnswer {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "sell_order_id", nullable = false) private UUID sellOrderId;
    @Column(name = "question_id") private UUID questionId;
    @Column(columnDefinition = "TEXT") private String question;
    @Column(length = 255) private String answer;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
}
