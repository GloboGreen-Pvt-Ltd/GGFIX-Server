package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.CategoryMenuType;
import com.repairshop.saas.masterdata.entity.MasterCategoryMenu;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterCategoryMenuRepository extends JpaRepository<MasterCategoryMenu, UUID> {

    List<MasterCategoryMenu> findAllByOrderBySortOrderAsc();

    List<MasterCategoryMenu> findAllByCategoryTypeOrderBySortOrderAsc(CategoryMenuType categoryType);

    /**
     * Rows still using one image URL — the guard before deleting a superseded
     * category-menu tile's image, same reasoning as {@code MasterBannerRepository}.
     */
    long countByImageUrl(String imageUrl);
}
