package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shop work the owner hands to employees — a delivery, parcel, supplier run,
 * bank work. Not a repair ticket. See migration 100.
 */
@Entity
@Table(name = "staff_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "task_type", length = 20, nullable = false)
    private String taskType;

    @Column(name = "title", length = 160, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "priority", length = 10, nullable = false)
    private String priority;

    @Column(name = "reminder_minutes", nullable = false)
    private Integer reminderMinutes;

    @Column(name = "repeat_rule", length = 10, nullable = false)
    private String repeatRule;

    @Column(name = "location", length = 500)
    private String location;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /** [{id, text, done}] */
    @Column(name = "checklist_json", columnDefinition = "TEXT")
    private String checklistJson;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "incomplete_reason", length = 500)
    private String incompleteReason;

    /** [{at, action, note, by}] — append-only. */
    @Column(name = "history_json", columnDefinition = "TEXT")
    private String historyJson;

    @Column(name = "client_ref", length = 64)
    private String clientRef;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<StaffTaskAssignee> assignees = new ArrayList<>();

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
