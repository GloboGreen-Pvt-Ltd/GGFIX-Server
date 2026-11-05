package com.repairshop.saas.auth.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Owner KYC documents, stored as a single jsonb blob on users.kyc_document.
 *
 * These are the shop OWNER's personal identity documents — Aadhar (front +
 * back) and PAN. Business documents (GST / Udyam) are NOT part of KYC; they
 * live per shop (shops.gst_certificate_url / udyam_certificate_url).
 *
 * Persisted via Hibernate's {@code @JdbcTypeCode(SqlTypes.JSON)} mapping on
 * {@link User#kycDocument} (Jackson serialises this POJO to jsonb).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KycDocument {

    private String aadharFrontUrl;
    private String aadharBackUrl;
    private String panUrl;

    /** PENDING_REVIEW | APPROVED | REJECTED. */
    private String status;

    /** Admin-supplied reason shown to the owner when status = REJECTED. */
    private String rejectReason;

    /** When the owner/admin last submitted (or resubmitted) the documents. */
    private Instant submittedAt;

    /** When an admin last approved / rejected the submission. */
    private Instant reviewedAt;

    /** True when at least one document URL is present. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean hasAnyDocument() {
        return notBlank(aadharFrontUrl) || notBlank(aadharBackUrl) || notBlank(panUrl);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
