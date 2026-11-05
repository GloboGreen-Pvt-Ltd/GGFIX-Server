package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** One employee on a staff task; name / role / phone are a snapshot. */
@Entity
@Table(name = "staff_task_assignees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffTaskAssignee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private StaffTask task;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(name = "name", length = 120)
    private String name;

    @Column(name = "role_label", length = 60)
    private String roleLabel;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;
}
