package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.ModelCompatibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModelCompatibilityRepository extends JpaRepository<ModelCompatibility, UUID> {

    List<ModelCompatibility> findAllByOrderBySortOrderAscBoxNoAsc();

    /**
     * Box numbers are compared case-insensitively so "A-12" and "a-12" cannot
     * become two boxes. The unique index on lower(box_no) is the real guarantee;
     * this is what lets the controller answer with a readable 409 instead of a
     * constraint-violation 500.
     */
    Optional<ModelCompatibility> findByBoxNoIgnoreCase(String boxNo);

    /** Guards type deletion: a type still holding boxes is refused, not silently orphaned. */
    long countByPartTypeId(UUID partTypeId);

    /**
     * Boxes still using one reference photo — the guard before deleting a superseded
     * one. Two boxes holding the same part legitimately share a photo.
     */
    long countByReferenceImageUrl(String referenceImageUrl);
}
