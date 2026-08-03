package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Knowledge-base attachment on a ticket. Schema lives in migration 36.
 *
 * `packType` is {@code REFERENCE} (existing solution the tech views) or
 * {@code NEW} (one the tech uploads after working out a fix). `fileUrl`
 * points at whatever the media-service produced when the file was
 * uploaded — could be an image, PDF, or video URL.
 */
@Entity
@Table(name = "ticket_solution_packs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketSolutionPack {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "pack_type", nullable = false, length = 20)
    private String packType;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "file_url", columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "model_id")
    private UUID modelId;

    @Column(name = "brand_name", length = 150)
    private String brandName;

    @Column(name = "model_name", length = 255)
    private String modelName;

    @Column(name = "issue_category", length = 80)
    private String issueCategory;

    @Column(name = "issue_subcategory", length = 120)
    private String issueSubcategory;

    @Column(name = "issue_category_id")
    private UUID issueCategoryId;

    @Column(name = "issue_subcategory_id")
    private UUID issueSubcategoryId;

    @Column(name = "files_json", columnDefinition = "TEXT")
    private String filesJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
