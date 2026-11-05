package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterBanner;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterBannerRepository extends JpaRepository<MasterBanner, UUID> {

    List<MasterBanner> findAllByOrderBySortOrderAsc();

    /**
     * Rows still using one image URL — the guard before deleting a superseded banner
     * image. Banners share artwork more readily than the other tables: a slide gets
     * duplicated to reorder it, and both copies then hold the same URL.
     */
    long countByImageUrl(String imageUrl);
}
