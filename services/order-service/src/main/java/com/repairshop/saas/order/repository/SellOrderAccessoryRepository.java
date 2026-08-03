package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.SellOrderAccessory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface SellOrderAccessoryRepository extends JpaRepository<SellOrderAccessory, UUID> {
    List<SellOrderAccessory> findBySellOrderId(UUID sellOrderId);

    @Modifying
    @Transactional
    void deleteBySellOrderId(UUID sellOrderId);
}
