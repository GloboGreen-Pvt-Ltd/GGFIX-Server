package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "technician_attendance", indexes = {
    @Index(name = "idx_tech_att_technician_date", columnList = "technician_id, attendance_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicianAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    @Column(name = "check_out_time")
    private LocalTime checkOutTime;

    @Column(name = "status", length = 50)
    private String status; // GENERAL, LEAVE, WEEK_OFF, LATE, PERMISSION

    @Column(name = "working_minutes")
    private Integer workingMinutes;

    @Column(name = "notes", length = 500)
    private String notes;

    // GPS proof for the 100m shop geofence — captured at the moment of each
    // punch (migration 65). Nullable so shops without saved coordinates, and
    // rows written before this feature, stay valid.
    @Column(name = "check_in_latitude", precision = 10, scale = 7)
    private java.math.BigDecimal checkInLatitude;

    @Column(name = "check_in_longitude", precision = 10, scale = 7)
    private java.math.BigDecimal checkInLongitude;

    @Column(name = "check_in_distance_meters")
    private Integer checkInDistanceMeters;

    @Column(name = "check_out_latitude", precision = 10, scale = 7)
    private java.math.BigDecimal checkOutLatitude;

    @Column(name = "check_out_longitude", precision = 10, scale = 7)
    private java.math.BigDecimal checkOutLongitude;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
